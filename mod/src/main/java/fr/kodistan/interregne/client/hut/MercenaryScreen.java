package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.network.messages.server.colony.HireMercenaryMessage;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import static com.minecolonies.api.util.constant.Constants.TICKS_FOURTY_MIN;

/**
 * Mercenaries to call in when the colony is attacked: how many come, and when they can be called again. Replaces
 * MineColonies' mercenary window.
 */
public final class MercenaryScreen extends ModuleScreen
{
    private MercenaryScreen(final IBuildingView townHall, @Nullable final Screen parent)
    {
        super("Mercenaires", townHall, parent, 320);
    }

    public static void open(final IBuildingView townHall, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new MercenaryScreen(townHall, parent));
    }

    /**
     * As MineColonies counts them: three, plus one per ten citizens.
     */
    private static int count(final IColonyView colony)
    {
        return colony.getCitizenCount() / 10 + 3;
    }

    /**
     * Ticks before they can be called again; 0 when they can.
     */
    private static long wait(final IColonyView colony)
    {
        final long used = colony.getMercenaryUseTime();
        if (used == 0)
        {
            return 0;
        }
        return Math.max(0, TICKS_FOURTY_MIN - (colony.getWorld().getGameTime() - used));
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final long wait = wait(building.getColony());
        y += Ui.banner(graphics, font, x, y, width, wait == 0 ? Ui.State.OK : Ui.State.WAIT, wait == 0 ? "Disponibles" : "Au repos",
          wait == 0 ? "ils arrivent dès qu'on les appelle" : "de nouveau disponibles dans " + (wait / 1200 + 1) + " min");
        return y + 19;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final IColonyView colony = building.getColony();
        final int count = count(colony);
        y = Ui.wrapped(graphics, font, net.minecraft.network.chat.Component.literal(
          "Une troupe de " + count + " mercenaires vient défendre la colonie pendant un moment. Il en vient un de plus par tranche de dix citoyens."), x + 2, y, width - 4,
          Palette.MUTED, 10) + 4;
        for (int i = 0; i < Math.min(count, 12); i++)
        {
            Icon.of(new ItemStack(Items.IRON_SWORD)).draw(graphics, x + 2 + i * 18, y, 16);
        }
        y += 24;
        final long wait = wait(colony);
        final String label = "Appeler les mercenaires";
        button(graphics, mouseX, mouseY, x + width - buttonWidth(label), y, label, Ui.ButtonStyle.PRIMARY, wait == 0,
          wait == 0 ? null : "Ils se reposent encore " + (wait / 1200 + 1) + " min.", () -> {
              colony.usedMercenaries();
              new HireMercenaryMessage(colony).sendToServer();
              minecraft.player.playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
              minecraft.setScreen(null);
          });
        return y + 18;
    }
}
