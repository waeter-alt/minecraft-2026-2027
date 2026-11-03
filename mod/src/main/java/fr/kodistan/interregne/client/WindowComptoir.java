package fr.kodistan.interregne.client;

import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import fr.kodistan.interregne.colony.BuildCosts;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.network.OpenComptoirPayload;
import fr.kodistan.interregne.network.RegionActionPayload;
import fr.kodistan.interregne.region.Crate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A Comptoir régional's window: the colony's region, production rate and countdown, the crates kept inside,
 * and the next level's price.
 */
public class WindowComptoir extends AbstractWindowSkeleton
{
    private final BlockPos comptoir;

    private OpenComptoirPayload state;
    private int                 secondsLeft;
    private int                 tick;

    private WindowComptoir(final OpenComptoirPayload state)
    {
        super("interregne:gui/comptoir.xml");
        this.comptoir = state.comptoir();
        registerButton("take", () -> send(RegionActionPayload.COMPTOIR_TAKE));
        registerButton("upgrade", () -> send(RegionActionPayload.COMPTOIR_UPGRADE));
        registerButton("close", this::close);
        show(state);
    }

    /**
     * Open the window, or refresh it when it is already showing this Comptoir.
     */
    public static void open(final OpenComptoirPayload state)
    {
        if (Minecraft.getInstance().screen instanceof BOScreen screen && screen.getWindow() instanceof WindowComptoir window && window.comptoir.equals(state.comptoir()))
        {
            window.show(state);
            return;
        }
        new WindowComptoir(state).open();
    }

    @Override
    public void onUpdate()
    {
        super.onUpdate();
        if (++tick % 20 == 0 && secondsLeft > 0)
        {
            secondsLeft--;
            showRate();
        }
    }

    private void show(final OpenComptoirPayload state)
    {
        this.state = state;
        this.secondsLeft = state.secondsLeft();
        final Crate crate = Crate.byId(state.region());

        findPaneOfTypeByID("colony", Text.class).setText(Component.literal(state.colonyName()));
        findPaneOfTypeByID("icon", ItemIcon.class).setItem(crate == null ? ItemStack.EMPTY : crate.stack(1));
        findPaneOfTypeByID("region", Text.class).setText(Component.literal(crate == null ? "Région pas encore attribuée" : capitalize(crate.regionName())));
        findPaneOfTypeByID("level", Text.class).setText(Component.literal("Niveau " + state.level() + " sur 5"));
        showRate();

        findPaneOfTypeByID("stock", Text.class).setText(Component.literal("Réserve : " + state.stock() + " / " + state.storage()
                                                                            + (state.stock() > 1 ? " caisses" : " caisse")));
        findPaneOfTypeByID("storage", Text.class).setText(Component.literal(state.warehouse()
                                                                              ? "La production est rangée dans l'entrepôt de la colonie."
                                                                              : "Pas d'entrepôt : la production reste ici, jusqu'à " + state.storage() + "."));
        final Button take = findPaneOfTypeByID("take", Button.class);
        take.setEnabled(state.stock() > 0);

        final Button upgrade = findPaneOfTypeByID("upgrade", Button.class);
        if (state.upgradeCost() < 0)
        {
            findPaneOfTypeByID("upgradeInfo", Text.class).setText(Component.literal("Niveau maximal atteint"));
            upgrade.setText(Component.literal("Améliorer"));
            upgrade.setEnabled(false);
        }
        else
        {
            findPaneOfTypeByID("upgradeInfo", Text.class).setText(Component.literal("Niveau " + (state.level() + 1) + " : toutes les "
                                                                                      + BuildCosts.formatSeconds(state.nextSeconds())));
            upgrade.setText(Component.literal("Améliorer au niveau " + (state.level() + 1) + " (" + Wallet.format(state.upgradeCost()) + ")"));
            upgrade.setEnabled(state.purse() >= state.upgradeCost() || Minecraft.getInstance().player.isCreative());
        }
    }

    private void showRate()
    {
        final StringBuilder colonyStock = new StringBuilder("Caisses à l'entrepôt :");
        for (final Crate each : Crate.values())
        {
            colonyStock.append('\n').append(each.displayName()).append(" : ").append(ClientCrateStock.get(each));
        }
        findPaneOfTypeByID("colonyStock", Text.class).setText(Component.literal(colonyStock.toString()));
        findPaneOfTypeByID("rate", Text.class).setText(Component.literal("Une caisse toutes les " + BuildCosts.formatSeconds(state.seconds())));
        findPaneOfTypeByID("next", Text.class).setText(Component.literal(state.region().isEmpty() ? "En attente d'une région"
                                                                           : "Prochaine caisse dans " + BuildCosts.formatSeconds(secondsLeft)));
    }

    private void send(final int action)
    {
        PacketDistributor.sendToServer(new RegionActionPayload(comptoir, action, 0, 0));
    }

    private static String capitalize(final String text)
    {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
