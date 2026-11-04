package fr.kodistan.interregne.colony;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.economy.RegionalTreasury;
import fr.kodistan.interregne.economy.Wallet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Intendance: when nobody in a colony can fill a request, it buys common items with the colony treasury
 * (kept at the colony's Banque régionale, where its members deposit)
 * after a grace period. The colony is never stuck for want of seeds or coal, but neglect costs gold.
 * Strategic items (rare, modded, or tagged interregne:intendance_excluded) are never bought: players get a clear
 * alert instead. A single bar per colony shows the next purchase's countdown and the total cost pending.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class Intendance
{
    /**
     * Items the Intendance never buys, editable by datapack.
     */
    public static final TagKey<Item> EXCLUDED = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "intendance_excluded"));

    /**
     * Basic items from other mods the Intendance buys all the same, such as MineColonies' large bottle for the Vacher.
     */
    public static final TagKey<Item> INCLUDED = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "intendance_included"));

    private static final int UPDATE_INTERVAL = 20;

    /**
     * Server tick when each unfilled request was first seen.
     */
    private static final Map<IToken<?>, Long> FIRST_SEEN = new HashMap<>();

    /**
     * Strategic requests already reported, so each alert is sent once.
     */
    private static final Set<IToken<?>> ALERTED = new HashSet<>();

    /**
     * The Intendance bar of each colony, by colony id; present only while purchases are pending.
     */
    private static final Map<Integer, ServerBossEvent> BARS = new HashMap<>();

    private static long now;

    /**
     * A request the Intendance will buy.
     */
    public record Purchase(IToken<?> token, IBuilding building, ItemStack stack, int cost, long dueAt)
    {
    }

    /**
     * What the colony's workers wait on that nobody in it can supply: what the Intendance can buy, and how many
     * strategic items it never provides.
     */
    public record Supplies(List<Purchase> buyable, List<Purchase> rare)
    {
        public int cost()
        {
            return buyable.stream().mapToInt(Purchase::cost).sum();
        }
    }

    public static Supplies supplies(final IColony colony)
    {
        final IRequestManager manager = colony.getRequestManager();
        final List<Purchase> buyable = new ArrayList<>();
        final List<Purchase> rare = new ArrayList<>();
        for (final IToken<?> token : manager.getPlayerResolver().getAllAssignedRequests())
        {
            final IRequest<?> request = manager.getRequestForToken(token);
            if (request == null || !(request.getRequest() instanceof IDeliverable deliverable) || request.getDisplayStacks().isEmpty())
            {
                continue;
            }
            final IBuilding building = colony.getBuildingManager().getBuilding(request.getRequester().getLocation().getInDimensionLocation());
            if (building == null)
            {
                continue;
            }
            final ItemStack wanted = request.getDisplayStacks().getFirst().copyWithCount(Math.max(1, deliverable.getCount()));
            if (isBuyable(wanted))
            {
                buyable.add(new Purchase(token, building, wanted, costOf(wanted), 0));
            }
            else
            {
                rare.add(new Purchase(token, building, wanted, -1, 0));
            }
        }
        return new Supplies(buyable, rare);
    }

    /**
     * Deliver an already paid purchase into the requesting hut and close its request.
     *
     * @return false when the hut is gone or full, so nothing was delivered.
     */
    public static boolean fits(final Purchase purchase)
    {
        final IItemHandler hut = inventory(purchase.building());
        return hut != null && ItemHandlerHelper.insertItemStacked(hut, purchase.stack().copy(), true).isEmpty();
    }

    public static boolean deliver(final IColony colony, final Purchase purchase)
    {
        final IItemHandler hut = inventory(purchase.building());
        if (hut == null || !open(colony.getRequestManager(), purchase) || !ItemHandlerHelper.insertItemStacked(hut, purchase.stack().copy(), true).isEmpty())
        {
            return false;
        }
        ItemHandlerHelper.insertItemStacked(hut, purchase.stack().copy(), false);
        close(colony.getRequestManager(), purchase);
        return true;
    }

    /**
     * Whether the request is still waiting: closing one request can close others with it (a parent and its parts), so
     * a list read a moment ago may name requests that are gone.
     */
    private static boolean open(final IRequestManager manager, final Purchase purchase)
    {
        try
        {
            return manager.getRequestForToken(purchase.token()) != null;
        }
        catch (final IllegalArgumentException e)
        {
            return false;
        }
    }

    private static void close(final IRequestManager manager, final Purchase purchase)
    {
        try
        {
            manager.overruleRequest(purchase.token(), purchase.stack().copy());
        }
        catch (final IllegalArgumentException e)
        {
            // Closed meanwhile along with another request: the goods are in the hut all the same
        }
        FIRST_SEEN.remove(purchase.token());
    }

    private Intendance()
    {
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        if (++now % UPDATE_INTERVAL != 0)
        {
            return;
        }
        if (!Config.INTENDANCE.get())
        {
            BARS.values().forEach(ServerBossEvent::removeAllPlayers);
            BARS.clear();
            return;
        }

        final Set<IToken<?>> stillOpen = new HashSet<>();
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            try
            {
                tickColony(colony, stillOpen);
            }
            catch (final RuntimeException e)
            {
                InterregneMod.LOGGER.error("Intendance failed for colony {}", colony.getID(), e);
            }
        }
        FIRST_SEEN.keySet().retainAll(stillOpen);
        ALERTED.retainAll(stillOpen);
    }

    @SubscribeEvent
    public static void onServerStopping(final ServerStoppingEvent event)
    {
        BARS.values().forEach(ServerBossEvent::removeAllPlayers);
        BARS.clear();
        FIRST_SEEN.clear();
        ALERTED.clear();
    }

    private static void tickColony(final IColony colony, final Set<IToken<?>> stillOpen)
    {
        final IRequestManager manager = colony.getRequestManager();
        final long graceTicks = Config.GRACE_SECONDS.get() * 20L;
        final long alertTicks = Config.ALERT_SECONDS.get() * 20L;
        final List<Purchase> pending = new ArrayList<>();

        for (final IToken<?> token : manager.getPlayerResolver().getAllAssignedRequests())
        {
            final IRequest<?> request = manager.getRequestForToken(token);
            if (request == null || !(request.getRequest() instanceof IDeliverable deliverable) || request.getDisplayStacks().isEmpty())
            {
                continue;
            }
            final IBuilding building = colony.getBuildingManager().getBuilding(request.getRequester().getLocation().getInDimensionLocation());
            if (building == null)
            {
                continue;
            }
            stillOpen.add(token);
            final long seen = FIRST_SEEN.computeIfAbsent(token, t -> now);

            final ItemStack wanted = request.getDisplayStacks().getFirst().copyWithCount(Math.max(1, deliverable.getCount()));
            if (!isBuyable(wanted))
            {
                if (now - seen >= alertTicks && ALERTED.add(token))
                {
                    alert(colony, manager, request, wanted);
                }
                continue;
            }
            pending.add(new Purchase(token, building, wanted, costOf(wanted), seen + graceTicks));
        }

        // Buy what is due, oldest first, as long as the treasury can pay
        pending.sort((a, b) -> Long.compare(a.dueAt(), b.dueAt()));
        final List<Purchase> waiting = new ArrayList<>();
        boolean broke = false;
        for (final Purchase purchase : pending)
        {
            if (purchase.dueAt() > now)
            {
                waiting.add(purchase);
                continue;
            }
            if (broke || !buy(colony, manager, purchase))
            {
                broke = true;
                waiting.add(purchase);
            }
        }
        updateBar(colony, waiting, broke);
    }

    private static boolean isBuyable(final ItemStack stack)
    {
        if (stack.is(INCLUDED))
        {
            return true;
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft")
                 && stack.getRarity() == Rarity.COMMON
                 && !stack.is(EXCLUDED);
    }

    private static int costOf(final ItemStack stack)
    {
        return Math.max(1, (int) Math.ceil((double) stack.getCount() / Config.ITEMS_PER_CROWN.get()));
    }

    /**
     * Pay from the town hall and deliver into the requesting hut, then close the request the way a player
     * filling it by hand does.
     */
    private static boolean buy(final IColony colony, final IRequestManager manager, final Purchase purchase)
    {
        final RegionalTreasury treasury = RegionalTreasury.get(colony.getWorld().getServer());
        final IItemHandler hut = inventory(purchase.building());
        if (hut == null)
        {
            return false;
        }
        if (!open(manager, purchase))
        {
            // Closed along with another request this very pass: nothing to buy
            return true;
        }
        if (treasury.balance(colony) < purchase.cost())
        {
            return false;
        }
        final ItemStack leftover = ItemHandlerHelper.insertItemStacked(hut, purchase.stack().copy(), true);
        if (!leftover.isEmpty())
        {
            // The hut is full: try again next time rather than lose the goods
            return true;
        }
        treasury.spend(colony, purchase.cost());
        ItemHandlerHelper.insertItemStacked(hut, purchase.stack().copy(), false);
        close(manager, purchase);

        colony.getMessagePlayerEntities().forEach(player -> {
            player.sendSystemMessage(Component.literal("⚖ Intendance : " + purchase.stack().getCount() + " × ")
                                       .append(purchase.stack().getHoverName())
                                       .append(" achetés pour " + Wallet.couronnes(purchase.cost(), null))
                                       .withStyle(ChatFormatting.GOLD));
            player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.4F, 0.7F);
        });
        return true;
    }

    /**
     * One bar per colony, shown only to its players and only while something is pending:
     * the countdown to the next purchase and what all pending purchases will cost.
     */
    private static void updateBar(final IColony colony, final List<Purchase> waiting, final boolean broke)
    {
        if (waiting.isEmpty())
        {
            final ServerBossEvent bar = BARS.remove(colony.getID());
            if (bar != null)
            {
                bar.removeAllPlayers();
            }
            return;
        }
        final ServerBossEvent bar = BARS.computeIfAbsent(colony.getID(),
          id -> new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS));

        final int total = waiting.stream().mapToInt(Purchase::cost).sum();
        final long next = waiting.stream().mapToLong(Purchase::dueAt).min().orElse(now);
        final long graceTicks = Math.max(1, Config.GRACE_SECONDS.get() * 20L);
        final String count = waiting.size() + (waiting.size() > 1 ? " achats" : " achat");

        if (broke)
        {
            final long owned = RegionalTreasury.get(colony.getWorld().getServer()).balance(colony);
            bar.setColor(BossEvent.BossBarColor.RED);
            bar.setProgress(1);
            bar.setName(Component.literal("⚖ Intendance · " + count + " · " + total + " couronnes — trésor insuffisant (" + Wallet.format(owned) + ", à déposer à la Banque régionale)"));
        }
        else
        {
            final long remaining = Math.max(0, next - now);
            bar.setColor(BossEvent.BossBarColor.YELLOW);
            bar.setProgress(Math.clamp(1.0F - (float) remaining / graceTicks, 0.0F, 1.0F));
            final long seconds = (remaining + 19) / 20;
            bar.setName(Component.literal("⚖ Intendance · " + count + " · " + total + " couronnes — prochain dans " + seconds / 60 + ":" + String.format("%02d", seconds % 60)));
        }

        final List<ServerPlayer> viewers = colony.getMessagePlayerEntities().stream()
                                             .filter(ServerPlayer.class::isInstance)
                                             .map(ServerPlayer.class::cast)
                                             .toList();
        for (final ServerPlayer player : List.copyOf(bar.getPlayers()))
        {
            if (!viewers.contains(player))
            {
                bar.removePlayer(player);
            }
        }
        viewers.forEach(bar::addPlayer);
    }

    private static void alert(final IColony colony, final IRequestManager manager, final IRequest<?> request, final ItemStack wanted)
    {
        final Component who = request.getRequester().getRequesterDisplayName(manager, request);
        colony.getMessagePlayerEntities().forEach(player -> player.sendSystemMessage(
          Component.literal("⚠ " + colony.getName() + " : ")
            .append(who)
            .append(" attend " + wanted.getCount() + " × ")
            .append(wanted.getHoverName())
            .append(" — l'Intendance ne fournit pas cet objet.")
            .withStyle(ChatFormatting.RED)));
    }

    @Nullable
    private static IItemHandler inventory(final IBuilding building)
    {
        return building.getTileEntity() == null ? null : building.getItemHandlerCap((Direction) null);
    }
}
