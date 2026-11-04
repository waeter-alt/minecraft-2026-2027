package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHallView;
import com.minecolonies.core.network.messages.server.colony.building.BuildRequestMessage;
import fr.kodistan.interregne.client.ClientTreasury;
import fr.kodistan.interregne.client.CrateRows;
import fr.kodistan.interregne.colony.BuildCosts;
import fr.kodistan.interregne.colony.TownHallCap;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.mixin.client.BossHealthOverlayAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A building's next level: what it costs, whether it can be bought now and why not. Shared by the hut window and the
 * colony hub, so both say the same thing.
 */
record UpgradeOffer(int level, int target, @Nullable String refusal, boolean free, int cost, boolean affordable, List<CrateRows.Row> crates,
                    boolean cratesReady, int seconds)
{
    static UpgradeOffer of(final IBuildingView building)
    {
        final Minecraft minecraft = Minecraft.getInstance();
        final int level = building.getBuildingLevel();
        final int target = level + 1;
        final ITownHallView townHall = building.getColony().getTownHall();
        final String refusal = TownHallCap.refusal(building instanceof ITownHallView, target, townHall == null ? 0 : townHall.getBuildingLevel());
        final boolean free = minecraft.player.isCreative();
        final long owned = Wallet.value(new PlayerMainInvWrapper(minecraft.player.getInventory())) + ClientTreasury.of(building.getColony());
        final int cost = BuildCosts.forLevel(target);
        final List<CrateRows.Row> crates = CrateRows.forBuilding(building, true);
        return new UpgradeOffer(level, target, refusal, free, cost, free || owned >= cost, crates, crates.stream().allMatch(CrateRows.Row::available),
          BuildCosts.secondsForLevel(target));
    }

    boolean canBuy()
    {
        return refusal == null && affordable && cratesReady;
    }

    String price()
    {
        return free ? "Gratuit (créatif)" : cost + " Couronnes";
    }

    /**
     * The main button's label: the action and how long its site lasts when it can be done, otherwise what is missing.
     */
    String action()
    {
        return refusal != null ? "Hôtel de ville niv. " + target + " requis"
                 : !affordable ? "Pas assez de Couronnes"
                     : !cratesReady ? "Il manque des caisses"
                         : (level == 0 ? "Construire" : "Améliorer") + " (" + BuildCosts.formatSeconds(seconds) + ")";
    }

    void buy(final IBuildingView building)
    {
        new BuildRequestMessage(building, BuildRequestMessage.Mode.BUILD, BlockPos.ZERO).sendToServer();
    }

    /**
     * How far this building's site is, read from the boss bar our sites show to nearby players; null when none is
     * on screen. A bar naming the building wins; otherwise the only site bar, if there is just one.
     */
    @Nullable
    static Float siteProgress(final IBuildingView building, final String shownName)
    {
        final Map<?, LerpingBossEvent> events = ((BossHealthOverlayAccessor) Minecraft.getInstance().gui.getBossOverlay()).interregne$events();
        final String name = Component.translatable(building.getBuildingDisplayName()).getString();
        LerpingBossEvent only = null;
        int sites = 0;
        for (final LerpingBossEvent event : events.values())
        {
            final String text = event.getName().getString();
            if (!text.startsWith("⚒"))
            {
                continue;
            }
            if (text.contains(name) || text.contains(shownName))
            {
                return event.getProgress();
            }
            only = event;
            sites++;
        }
        return sites == 1 ? only.getProgress() : null;
    }
}
