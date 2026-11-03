package fr.kodistan.interregne.politics;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.economy.ImperialBank;
import fr.kodistan.interregne.region.Crate;
import fr.kodistan.interregne.region.RegionData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The imperial tithe: every few days each colony owes a basket of crates of the other regions, delivered at the
 * Trône impérial. Paying earns Influence; missing the deadline costs the colony owner Influence and raises the Grogne.
 * The cycle runs on its own once launched (autopilot), so absent Intendants never stall it.
 */
public final class Tithe
{
    private Tithe()
    {
    }

    public static String colonyKey(final IColony colony)
    {
        return colony.getDimension().location() + ":" + colony.getID();
    }

    /**
     * What a colony owes for the current tithe: two crates (by default) of each type except its own region's.
     */
    public static Map<Crate, Integer> basket(final MinecraftServer server, final IColony colony)
    {
        final Map<Crate, Integer> basket = new EnumMap<>(Crate.class);
        final Crate own = RegionData.get(server).regionOf(colony);
        final double factor = Empire.get(server).titheWeight().factor();
        final int perType = Config.TITHE_PER_TYPE.get();
        for (final Crate crate : Crate.values())
        {
            if (crate != own && perType > 0)
            {
                basket.put(crate, Math.max(1, (int) Math.round(perType * factor)));
            }
        }
        return basket;
    }

    /**
     * Crates still owed by a colony for the current tithe.
     */
    public static Map<Crate, Integer> remaining(final MinecraftServer server, final IColony colony)
    {
        final Map<String, Integer> done = Empire.get(server).deliveredBy(colonyKey(colony));
        final Map<Crate, Integer> left = new EnumMap<>(Crate.class);
        basket(server, colony).forEach((crate, owed) -> {
            final int missing = owed - done.getOrDefault(crate.id(), 0);
            if (missing > 0)
            {
                left.put(crate, missing);
            }
        });
        return left;
    }

    /**
     * Take the crates the player's colony still owes from the player's inventory.
     */
    public static void deliver(final ServerPlayer player)
    {
        final MinecraftServer server = player.server;
        final Empire empire = Empire.get(server);
        final IColony colony = ImperialBank.colonyOf(player);
        if (!empire.titheActive())
        {
            player.sendSystemMessage(Component.literal("Aucune dîme n'est en cours.").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (colony == null)
        {
            player.sendSystemMessage(Component.literal("Tu n'appartiens à aucune colonie.").withStyle(ChatFormatting.RED));
            return;
        }

        final Map<String, Integer> done = empire.deliveredBy(colonyKey(colony));
        final Inventory inventory = player.getInventory();
        int given = 0;
        for (final Map.Entry<Crate, Integer> owed : remaining(server, colony).entrySet())
        {
            int left = owed.getValue();
            for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++)
            {
                final ItemStack stack = inventory.getItem(slot);
                if (stack.is(owed.getKey().item()))
                {
                    final int taken = Math.min(left, stack.getCount());
                    stack.shrink(taken);
                    left -= taken;
                    given += taken;
                    done.merge(owed.getKey().id(), taken, Integer::sum);
                }
            }
        }
        empire.markDirty();

        if (given == 0)
        {
            player.sendSystemMessage(Component.literal("Tu ne portes aucune caisse due. Reste à livrer : " + describe(remaining(server, colony))).withStyle(ChatFormatting.GOLD));
            return;
        }
        Influence.add(server, player.getUUID(), (long) given * Config.TITHE_BONUS.get(), "dîme livrée");
        player.playNotifySound(SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6F, 1.2F);
        final Map<Crate, Integer> left = remaining(server, colony);
        player.sendSystemMessage(Component.literal(given + (given > 1 ? " caisses livrées" : " caisse livrée") + " à l'Empire. "
                                                     + (left.isEmpty() ? "La dîme de " + colony.getName() + " est acquittée !" : "Reste : " + describe(left)))
                                   .withStyle(ChatFormatting.GOLD));
    }

    /**
     * Start a tithe now, due after the configured number of hours.
     */
    public static void launch(final MinecraftServer server)
    {
        Empire.get(server).startTithe(System.currentTimeMillis() + Config.TITHE_HOURS.get() * 3_600_000L);
        Proclamations.announce(server, "La dîme impériale est exigée. Chaque colonie doit livrer ses caisses au Trône impérial avant "
                                         + Proclamations.formatDelay(Config.TITHE_HOURS.get() * 3_600_000L) + ".");
    }

    /**
     * Called regularly: settle the tithe at its deadline and start the next one.
     */
    public static void tick(final MinecraftServer server)
    {
        final Empire empire = Empire.get(server);
        if (!empire.titheActive() || System.currentTimeMillis() < empire.titheDueAt())
        {
            return;
        }
        int defaulters = 0;
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            final Map<Crate, Integer> left = remaining(server, colony);
            if (left.isEmpty())
            {
                continue;
            }
            defaulters++;
            final int penalty = Config.TITHE_PENALTY.get();
            Influence.remove(server, colony.getPermissions().getOwner(), penalty, "dîme impayée");
            empire.addTreasury(100L * left.values().stream().mapToInt(Integer::intValue).sum());
            server.getPlayerList().broadcastSystemMessage(Component.literal("⚜ " + colony.getName() + " n'a pas acquitté la dîme : son seigneur perd "
                                                                              + penalty + " d'Influence.").withStyle(ChatFormatting.DARK_PURPLE), false);
        }
        if (defaulters > 0)
        {
            empire.addGrogne(defaulters);
        }
        empire.endTithe();
        launch(server);
    }

    /**
     * "2 Caisse de bois, 1 Caisse de pierre"
     */
    public static String describe(final Map<Crate, Integer> crates)
    {
        if (crates.isEmpty())
        {
            return "rien";
        }
        final StringBuilder text = new StringBuilder();
        crates.forEach((crate, count) -> text.append(text.isEmpty() ? "" : ", ").append(count).append(" × ").append(crate.displayName()));
        return text.toString();
    }

    @Nullable
    public static IColony colonyOf(final ServerPlayer player)
    {
        return ImperialBank.colonyOf(player);
    }
}
