package fr.kodistan.interregne.politics;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Action;
import fr.kodistan.interregne.network.DiplomacyActionPayload;
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
        final Alliances alliances = get(player.server);
        final IColony other = IColonyManager.getInstance().getColonyByWorld(payload.target(), player.serverLevel());
        final boolean mayAct = colony.getPermissions().hasPermission(player, Action.EDIT_PERMISSIONS);
        if (payload.action() != DiplomacyActionPayload.REFRESH && mayAct && other != null && other.getID() != colony.getID())
        {
            final int me = colony.getID();
            final int you = other.getID();
            final int before = alliances.status(me, you);
            switch (payload.action())
            {
                case DiplomacyActionPayload.PROPOSE -> alliances.propose(me, you);
                case DiplomacyActionPayload.ACCEPT -> alliances.accept(me, you);
                case DiplomacyActionPayload.WITHDRAW -> alliances.withdraw(me, you);
                case DiplomacyActionPayload.BREAK -> alliances.breakOff(me, you);
                default ->
                {
                }
            }
            final int after = alliances.status(me, you);
            if (after != before)
            {
                tell(player.server, colony, other, after);
                PacketDistributor.sendToAllPlayers(alliances.syncOf(colony, player.serverLevel()));
                PacketDistributor.sendToAllPlayers(alliances.syncOf(other, player.serverLevel()));
                return;
            }
        }
        PacketDistributor.sendToPlayer(player, alliances.syncOf(colony, player.serverLevel()));
    }

    /**
     * Tells the other colony's online members what just happened between the two.
     */
    private static void tell(final MinecraftServer server, final IColony colony, final IColony other, final int status)
    {
        final String text = switch (status)
        {
            case SENT -> colony.getName() + " vous propose une alliance. Répondez-lui depuis l'onglet Diplomatie de l'hôtel de ville.";
            case ALLIED -> "Alliance conclue entre " + colony.getName() + " et " + other.getName() + ".";
            default -> "Les liens avec " + colony.getName() + " ont changé : plus d'alliance ni de proposition.";
        };
        for (final ServerPlayer member : server.getPlayerList().getPlayers())
        {
            if (other.getPermissions().isColonyMember(member))
            {
                member.sendSystemMessage(Component.literal(text));
            }
        }
    }

    private DiplomacySyncPayload syncOf(final IColony colony, final net.minecraft.server.level.ServerLevel level)
    {
        final List<DiplomacySyncPayload.Entry> entries = new ArrayList<>();
        for (final IColony other : IColonyManager.getInstance().getColonies(level))
        {
            if (other.getID() == colony.getID())
            {
                continue;
            }
            entries.add(new DiplomacySyncPayload.Entry(other.getID(), other.getName(), other.getPermissions().getOwnerName(),
              other.getCitizenManager().getCurrentCitizenCount(), other.getCenter().getX(), other.getCenter().getZ(),
              status(colony.getID(), other.getID())));
        }
        return new DiplomacySyncPayload(colony.getID(), entries);
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
