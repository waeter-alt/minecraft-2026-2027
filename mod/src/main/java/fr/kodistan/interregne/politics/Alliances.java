package fr.kodistan.interregne.politics;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Action;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.economy.RegionalTreasury;
import fr.kodistan.interregne.garrison.Garrison;
import fr.kodistan.interregne.network.DiplomacyActionPayload;
import fr.kodistan.interregne.routes.RouteRegistry;
import fr.kodistan.interregne.routes.WarRoad;
import fr.kodistan.interregne.war.WarAssaults;
import fr.kodistan.interregne.war.WarMarches;
import fr.kodistan.interregne.network.DiplomacySyncPayload;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The alliances between colonies, kept by colony id: Kodistan's own system, which stands in for MineColonies' rank-based
 * one. An alliance exists only once both sides agreed: one colony proposes, the other accepts. Either side may break it
 * or withdraw a proposal. Trade routes will hang on these pairs.
 */
public final class Alliances extends SavedData
{
    private static final String NAME = "interregne_alliances";

    public static final int NONE = 0;
    public static final int ALLIED = 1;
    /** This colony proposed and waits for the answer. */
    public static final int SENT = 2;
    /** The other colony proposed to this one. */
    public static final int RECEIVED = 3;
    /** This colony declared war, the notice still runs. */
    public static final int WAR_OUT_NOTICE = 4;
    /** This colony is at war as the attacker. */
    public static final int WAR_OUT = 5;
    /** The other colony declared war on this one, the notice still runs. */
    public static final int WAR_IN_NOTICE = 6;
    /** The other colony is at war with this one as the attacker. */
    public static final int WAR_IN = 7;

    private final Set<Long> allied = new HashSet<>();
    /** Proposals as {@code from << 32 | to}. */
    private final Set<Long> proposals = new HashSet<>();

    public static Alliances get(final MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Alliances::new, Alliances::load, null), NAME);
    }

    private static long pair(final int a, final int b)
    {
        return (long) Math.min(a, b) << 32 | Math.max(a, b);
    }

    private static long directed(final int from, final int to)
    {
        return (long) from << 32 | to;
    }

    public int status(final int me, final int other)
    {
        if (allied.contains(pair(me, other)))
        {
            return ALLIED;
        }
        if (proposals.contains(directed(me, other)))
        {
            return SENT;
        }
        return proposals.contains(directed(other, me)) ? RECEIVED : NONE;
    }

    public boolean areAllied(final int a, final int b)
    {
        return allied.contains(pair(a, b));
    }

    /**
     * A proposal, or the answer to the other side's own: two crossing proposals make the alliance.
     */
    private void propose(final int me, final int other)
    {
        if (allied.contains(pair(me, other)))
        {
            return;
        }
        if (proposals.contains(directed(other, me)))
        {
            accept(me, other);
            return;
        }
        proposals.add(directed(me, other));
        setDirty();
    }

    private void accept(final int me, final int other)
    {
        if (proposals.remove(directed(other, me)))
        {
            proposals.remove(directed(me, other));
            allied.add(pair(me, other));
            setDirty();
        }
    }

    /**
     * Refuses the other side's proposal, or withdraws one's own.
     */
    private void withdraw(final int me, final int other)
    {
        if (proposals.remove(directed(other, me)) | proposals.remove(directed(me, other)))
        {
            setDirty();
        }
    }

    private void breakOff(final int me, final int other)
    {
        if (allied.remove(pair(me, other)))
        {
            setDirty();
        }
    }

    public static void onAction(final DiplomacyActionPayload payload, final IPayloadContext context)
    {
        if (!(context.player() instanceof ServerPlayer player))
        {
            return;
        }
        final IColony colony = IColonyManager.getInstance().getColonyByWorld(payload.colony(), player.serverLevel());
        if (colony == null || !colony.getPermissions().isColonyMember(player))
        {
            return;
        }
        final IColony other = IColonyManager.getInstance().getColonyByWorld(payload.target(), player.serverLevel());
        final boolean mayAct = colony.getPermissions().hasPermission(player, Action.EDIT_PERMISSIONS);
        if (payload.action() != DiplomacyActionPayload.REFRESH && mayAct && other != null && other.getID() != colony.getID())
        {
            final String refusal = switch (payload.action())
            {
                case DiplomacyActionPayload.LAUNCH_ASSAULT -> WarMarches.start(player.server, colony, other, payload.amount() / 1000, payload.amount() % 1000);
                case DiplomacyActionPayload.RECALL_TROOPS -> WarMarches.marching(colony.getID(), other.getID())
                  ? WarMarches.recall(player.server, colony, other.getID())
                  : WarAssaults.recall(colony, other.getID());
                case DiplomacyActionPayload.REFILL_AID -> AllyAid.refill(player.server, colony, other);
                default -> Diplomacy.apply(player, colony, other, payload.action());
            };
            if (refusal == null && (payload.action() == DiplomacyActionPayload.LAUNCH_ASSAULT || payload.action() == DiplomacyActionPayload.RECALL_TROOPS
                                      || payload.action() == DiplomacyActionPayload.REFILL_AID))
            {
                broadcast(player.server, colony, other);
            }
            if (refusal != null)
            {
                player.sendSystemMessage(Component.literal(refusal).withStyle(net.minecraft.ChatFormatting.RED));
            }
        }
        PacketDistributor.sendToPlayer(player, syncOf(player.server, colony, player.serverLevel()));
    }

    /**
     * Sends both colonies' views to every player, after something changed between them.
     */
    public static void broadcast(final MinecraftServer server, final IColony a, final IColony b)
    {
        PacketDistributor.sendToAllPlayers(syncOf(server, a, server.overworld()));
        PacketDistributor.sendToAllPlayers(syncOf(server, b, server.overworld()));
    }

    /**
     * Sends the player the diplomacy of every colony they belong to, so the war indicator is right after a relog.
     */
    public static void syncTo(final ServerPlayer player)
    {
        final net.minecraft.server.level.ServerLevel level = player.server.overworld();
        for (final IColony colony : IColonyManager.getInstance().getColonies(level))
        {
            if (colony.getPermissions().isColonyMember(player))
            {
                PacketDistributor.sendToPlayer(player, syncOf(player.server, colony, level));
            }
        }
    }

    static DiplomacySyncPayload syncOf(final MinecraftServer server, final IColony colony, final net.minecraft.server.level.ServerLevel level)
    {
        final Alliances alliances = get(server);
        final RouteRegistry routes = RouteRegistry.get(server);
        final AllyAid aid = AllyAid.get(server);
        final List<DiplomacySyncPayload.Entry> entries = new ArrayList<>();
        for (final IColony other : IColonyManager.getInstance().getColonies(level))
        {
            if (other.getID() == colony.getID())
            {
                continue;
            }
            final int me = colony.getID();
            final int you = other.getID();
            final int[] relation = Diplomacy.relation(server, alliances, me, you);
            final int road = WarRoad.building(me, you) ? DiplomacySyncPayload.ROAD_BUILDING
              : routes.pathBetween(me, you) != null ? DiplomacySyncPayload.ROAD_READY
              : WarRoad.impossible(me, you) ? DiplomacySyncPayload.ROAD_IMPOSSIBLE : DiplomacySyncPayload.ROAD_NONE;
            final int campaign = WarMarches.marching(me, you) ? DiplomacySyncPayload.CAMPAIGN_MARCHING
              : WarAssaults.assaulting(me, you) ? DiplomacySyncPayload.CAMPAIGN_ASSAULTING : DiplomacySyncPayload.CAMPAIGN_NONE;
            entries.add(new DiplomacySyncPayload.Entry(you, other.getName(), other.getPermissions().getOwnerName(),
              other.getCitizenManager().getCurrentCitizenCount(), other.getCenter().getX(), other.getCenter().getZ(), relation[0], relation[1], road, campaign,
              aid.reserve(me, you), aid.reserve(you, me), aid.isEngaged(me, you) ? aid.alive(me, you) : -1, aid.isEngaged(you, me) ? aid.alive(you, me) : -1,
              relation[0] == WAR_IN ? WarRules.surrenderSeconds(server, you, me) : DiplomacySyncPayload.SURRENDER_NO,
              relation[0] == WAR_OUT ? WarRules.lockHolderName(server, me, you) : "", relation[0] == WAR_OUT ? WarRules.lockSeconds(server, me, you) : 0));
        }
        return new DiplomacySyncPayload(colony.getID(), Garrison.available(server, colony, Garrison.LEGIONNAIRE), Garrison.available(server, colony, Garrison.ARCHERE),
          Config.WAR_DECLARATION_COST.get().intValue(), Config.WAR_TRIBUTE.get(), RegionalTreasury.get(server).balance(colony), AllyAid.price(), AllyAid.max(), entries);
    }

    // Package-private moves for Diplomacy, which holds the rules.
    void doPropose(final int me, final int other)
    {
        propose(me, other);
    }

    void doAccept(final int me, final int other)
    {
        accept(me, other);
    }

    void doWithdraw(final int me, final int other)
    {
        withdraw(me, other);
    }

    boolean doBreak(final int me, final int other)
    {
        final boolean was = allied.contains(pair(me, other));
        breakOff(me, other);
        return was;
    }

    /**
     * Clears an alliance and any proposal between the two, e.g. when one declares war on the other.
     */
    void clearBetween(final int a, final int b)
    {
        final boolean changed = allied.remove(pair(a, b)) | proposals.remove(directed(a, b)) | proposals.remove(directed(b, a));
        if (changed)
        {
            setDirty();
        }
    }

    /**
     * The colony is gone: its alliances and proposals are erased.
     *
     * @return the colonies that were allied with it or had proposals with it
     */
    Set<Integer> removeColony(final int colony)
    {
        final Set<Integer> others = new HashSet<>();
        allied.removeIf(pair -> {
            final int low = (int) (pair >> 32);
            final int high = pair.intValue();
            if (low == colony || high == colony)
            {
                others.add(low == colony ? high : low);
                return true;
            }
            return false;
        });
        proposals.removeIf(pair -> {
            final int from = (int) (pair >> 32);
            final int to = pair.intValue();
            if (from == colony || to == colony)
            {
                others.add(from == colony ? to : from);
                return true;
            }
            return false;
        });
        if (!others.isEmpty())
        {
            setDirty();
        }
        return others;
    }

    /** Every colony id this state mentions, for the purge of colonies that no longer exist. */
    Set<Integer> colonyIds()
    {
        final Set<Integer> ids = new HashSet<>();
        for (final long pair : allied)
        {
            ids.add((int) (pair >> 32));
            ids.add((int) pair);
        }
        for (final long pair : proposals)
        {
            ids.add((int) (pair >> 32));
            ids.add((int) pair);
        }
        return ids;
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        tag.putLongArray("allied", allied.stream().mapToLong(Long::longValue).toArray());
        tag.putLongArray("proposals", proposals.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    private static Alliances load(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        final Alliances alliances = new Alliances();
        for (final long value : tag.getLongArray("allied"))
        {
            alliances.allied.add(value);
        }
        for (final long value : tag.getLongArray("proposals"))
        {
            alliances.proposals.add(value);
        }
        return alliances;
    }
}
