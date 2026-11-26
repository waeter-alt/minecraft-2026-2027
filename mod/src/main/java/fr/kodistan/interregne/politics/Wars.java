package fr.kodistan.interregne.politics;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The wars declared between colonies, by colony id. A war is one-sided in who started it: the attacker declares, a
 * notice runs, then it is open until the attacker makes peace. Only an open war lets one colony assault the other.
 */
public final class Wars extends SavedData
{
    private static final String NAME = "interregne_wars";

    /** {@code attacker << 32 | defender} to the game time of the declaration. */
    private final Map<Long, Long> declared = new HashMap<>();
    /** The wars whose opening was already announced. */
    private final Set<Long> opened = new HashSet<>();
    /** Colonies that just lost a war, to the game time their shield ends: nobody may declare war on them before. */
    private final Map<Integer, Long> shields = new HashMap<>();
    /** What each war cost so far, for the end-of-war recap: see {@link #record}. */
    private final Map<Long, long[]> stats = new HashMap<>();
    /** Defender to {attacker, game time the lock ends}: who an assault on that defender is reserved to, see {@link #lockHolder}. */
    private final Map<Integer, long[]> locks = new HashMap<>();
    /** Assaults the defender repelled, per war: a war with one may be ended by the defender, see {@link #surrenderWait}. */
    private final Map<Long, Integer> repelled = new HashMap<>();
    /** Game time the last assault of the war ended, per war: where the attacker's inaction is counted from. */
    private final Map<Long, Long> lastAssaultEnd = new HashMap<>();

    /** Indices into a war's stats. */
    public static final int ASSAULTS = 0, SENT = 1, LOST = 2, DESTROYED = 3, LOOT = 4, FALLEN = 5, STAT_COUNT = 6;

    public static Wars get(final MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Wars::new, Wars::load, null), NAME);
    }

    private static long key(final int attacker, final int defender)
    {
        return (long) attacker << 32 | defender;
    }

    public boolean declaredBy(final int attacker, final int defender)
    {
        return declared.containsKey(key(attacker, defender));
    }

    /**
     * Whether either colony has declared war on the other.
     */
    public boolean between(final int a, final int b)
    {
        return declaredBy(a, b) || declaredBy(b, a);
    }

    /**
     * Ticks before the war of this attacker on this defender is open; 0 once it is (or when there is no such war).
     */
    public long noticeLeft(final int attacker, final int defender, final long now, final long noticeTicks)
    {
        final Long at = declared.get(key(attacker, defender));
        return at == null ? 0 : Math.max(0, at + noticeTicks - now);
    }

    /**
     * Whether the attacker may assault the defender: war declared and its notice over.
     */
    public boolean isOpen(final int attacker, final int defender, final long now, final long noticeTicks)
    {
        return declaredBy(attacker, defender) && noticeLeft(attacker, defender, now, noticeTicks) == 0;
    }

    public void declare(final int attacker, final int defender, final long now)
    {
        declared.put(key(attacker, defender), now);
        setDirty();
    }

    /**
     * Adds one finished assault of this war to its tally: troops sent and lost, buildings destroyed, crowns looted and
     * defenders fallen. Ignored when there is no such war (an assault ended by the peace itself).
     */
    public void record(final int attacker, final int defender, final int sent, final int lost, final int destroyed, final long loot, final int fallen)
    {
        final long key = key(attacker, defender);
        if (!declared.containsKey(key))
        {
            return;
        }
        final long[] tally = stats.computeIfAbsent(key, k -> new long[STAT_COUNT]);
        tally[ASSAULTS]++;
        tally[SENT] += sent;
        tally[LOST] += lost;
        tally[DESTROYED] += destroyed;
        tally[LOOT] += loot;
        tally[FALLEN] += fallen;
        setDirty();
    }

    /**
     * A copy of this war's tally (zeros when no assault ended yet).
     */
    public long[] stats(final int attacker, final int defender)
    {
        final long[] tally = stats.get(key(attacker, defender));
        return tally == null ? new long[STAT_COUNT] : tally.clone();
    }

    /**
     * The game time the war was declared, or -1.
     */
    public long declaredAt(final int attacker, final int defender)
    {
        final Long at = declared.get(key(attacker, defender));
        return at == null ? -1 : at;
    }

    /**
     * Whether any colony has declared war on this one (notice or open).
     */
    public boolean defending(final int defender)
    {
        for (final long key : declared.keySet())
        {
            if ((int) key == defender)
            {
                return true;
            }
        }
        return false;
    }

    public boolean end(final int attacker, final int defender)
    {
        stats.remove(key(attacker, defender));
        opened.remove(key(attacker, defender));
        repelled.remove(key(attacker, defender));
        lastAssaultEnd.remove(key(attacker, defender));
        final long[] lock = locks.get(defender);
        if (lock != null && lock[0] == attacker)
        {
            locks.remove(defender);
            setDirty();
        }
        final boolean was = declared.remove(key(attacker, defender)) != null;
        if (was)
        {
            setDirty();
        }
        return was;
    }

    /**
     * Wars whose notice just ran out and were not announced yet, as {attacker, defender} pairs. Marks them announced.
     */
    public java.util.List<int[]> takeNewlyOpen(final long now, final long noticeTicks)
    {
        final java.util.List<int[]> out = new java.util.ArrayList<>();
        for (final Map.Entry<Long, Long> entry : declared.entrySet())
        {
            if (now - entry.getValue() >= noticeTicks && opened.add(entry.getKey()))
            {
                out.add(new int[] {(int) (entry.getKey() >> 32), (int) (long) entry.getKey()});
            }
        }
        if (!out.isEmpty())
        {
            setDirty();
        }
        return out;
    }

    /**
     * The colony that has this defender reserved for its assaults at game time {@code now}, or -1 when none has (no
     * lock yet, or its window ran out).
     */
    public int lockHolder(final int defender, final long now)
    {
        final long[] lock = locks.get(defender);
        return lock == null || lock[1] <= now ? -1 : (int) lock[0];
    }

    /**
     * Ticks left on the defender's lock, 0 when it has none.
     */
    public long lockLeft(final int defender, final long now)
    {
        final long[] lock = locks.get(defender);
        return lock == null ? 0 : Math.max(0, lock[1] - now);
    }

    /**
     * The colony standing in the way of {@code attacker} against {@code defender} because of the lock, -1 when the way
     * is free (nobody holds the defender, or the attacker itself does).
     */
    public int lockedAgainst(final int attacker, final int defender, final long now)
    {
        final int holder = lockHolder(defender, now);
        return holder == attacker ? -1 : holder;
    }

    /**
     * An assault of {@code attacker} on {@code defender} is launched: when nobody holds the defender (no lock, or an
     * expired one), the attacker takes it for {@code lockTicks}. The window never grows by itself: further assaults of
     * the holder leave it as it is. The lock falls with the war it was taken for, see {@link #end}.
     */
    public void startLock(final int attacker, final int defender, final long now, final long lockTicks)
    {
        if (lockTicks <= 0 || lockHolder(defender, now) >= 0)
        {
            return;
        }
        locks.put(defender, new long[] {attacker, now + lockTicks});
        setDirty();
    }

    /**
     * An assault of this war just ended: remembers when, and whether the defender repelled it.
     */
    public void finishAssault(final int attacker, final int defender, final long now, final boolean repelledByDefender)
    {
        final long key = key(attacker, defender);
        if (!declared.containsKey(key))
        {
            return;
        }
        lastAssaultEnd.put(key, now);
        if (repelledByDefender)
        {
            repelled.merge(key, 1, Integer::sum);
        }
        setDirty();
    }

    public int repelled(final int attacker, final int defender)
    {
        return repelled.getOrDefault(key(attacker, defender), 0);
    }

    /**
     * When the defender may demand the attacker's surrender: 0 if it may now (it repelled an assault, or the attacker
     * has launched none for {@code idleTicks} since the notice ended or its last assault ended), the ticks to wait
     * for that inaction otherwise, -1 when it cannot be reached for now (no such open war, or the attacker's troops are
     * on the road or on the field).
     */
    public long surrenderWait(final int attacker, final int defender, final long now, final long noticeTicks, final long idleTicks, final boolean campaign)
    {
        final long key = key(attacker, defender);
        final Long at = declared.get(key);
        if (at == null || noticeLeft(attacker, defender, now, noticeTicks) > 0)
        {
            return -1;
        }
        if (repelled.getOrDefault(key, 0) > 0)
        {
            return 0;
        }
        if (campaign)
        {
            return -1;
        }
        final long since = Math.max(at + noticeTicks, lastAssaultEnd.getOrDefault(key, 0L));
        return Math.max(0, since + idleTicks - now);
    }

    /**
     * The colony is gone: every war it declared or suffered ends without winner, its shield and tallies with it.
     *
     * @return the colonies that were at war with it
     */
    public Set<Integer> removeColony(final int colony)
    {
        final Set<Integer> opponents = new java.util.HashSet<>();
        for (final long key : new java.util.ArrayList<>(declared.keySet()))
        {
            final int attacker = (int) (key >> 32);
            final int defender = (int) key;
            if (attacker == colony || defender == colony)
            {
                opponents.add(attacker == colony ? defender : attacker);
                end(attacker, defender);
            }
        }
        if (shields.remove(colony) != null)
        {
            setDirty();
        }
        return opponents;
    }

    /** Every colony id this state mentions, for the purge of colonies that no longer exist. */
    public Set<Integer> colonyIds()
    {
        final Set<Integer> ids = new java.util.HashSet<>(shields.keySet());
        for (final long key : declared.keySet())
        {
            ids.add((int) (key >> 32));
            ids.add((int) key);
        }
        return ids;
    }

    public void shield(final int colony, final long until)
    {
        shields.put(colony, until);
        setDirty();
    }

    /**
     * Ticks left on the colony's shield, 0 when it has none.
     */
    public long shieldLeft(final int colony, final long now)
    {
        final Long until = shields.get(colony);
        return until == null ? 0 : Math.max(0, until - now);
    }

    public boolean hasPending(final long now, final long noticeTicks)
    {
        for (final Map.Entry<Long, Long> entry : declared.entrySet())
        {
            if (!opened.contains(entry.getKey()))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        tag.putLongArray("keys", declared.keySet().stream().mapToLong(Long::longValue).toArray());
        tag.putLongArray("times", declared.values().stream().mapToLong(Long::longValue).toArray());
        tag.putLongArray("opened", opened.stream().mapToLong(Long::longValue).toArray());
        tag.putIntArray("shieldIds", shields.keySet().stream().mapToInt(Integer::intValue).toArray());
        tag.putLongArray("shieldUntil", shields.values().stream().mapToLong(Long::longValue).toArray());
        final CompoundTag tallies = new CompoundTag();
        stats.forEach((key, tally) -> tallies.putLongArray(Long.toString(key), tally));
        tag.put("stats", tallies);
        tag.putLongArray("lockDefenders", locks.keySet().stream().mapToLong(Integer::longValue).toArray());
        tag.putLongArray("lockAttackers", locks.values().stream().mapToLong(l -> l[0]).toArray());
        tag.putLongArray("lockUntil", locks.values().stream().mapToLong(l -> l[1]).toArray());
        tag.putLongArray("repelledKeys", repelled.keySet().stream().mapToLong(Long::longValue).toArray());
        tag.putIntArray("repelledCounts", repelled.values().stream().mapToInt(Integer::intValue).toArray());
        tag.putLongArray("endKeys", lastAssaultEnd.keySet().stream().mapToLong(Long::longValue).toArray());
        tag.putLongArray("endTimes", lastAssaultEnd.values().stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    private static Wars load(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        final Wars wars = new Wars();
        final long[] keys = tag.getLongArray("keys");
        final long[] times = tag.getLongArray("times");
        for (int i = 0; i < Math.min(keys.length, times.length); i++)
        {
            wars.declared.put(keys[i], times[i]);
        }
        for (final long value : tag.getLongArray("opened"))
        {
            wars.opened.add(value);
        }
        final int[] shieldIds = tag.getIntArray("shieldIds");
        final long[] shieldUntil = tag.getLongArray("shieldUntil");
        for (int i = 0; i < Math.min(shieldIds.length, shieldUntil.length); i++)
        {
            wars.shields.put(shieldIds[i], shieldUntil[i]);
        }
        final CompoundTag tallies = tag.getCompound("stats");
        for (final String key : tallies.getAllKeys())
        {
            final long[] saved = tallies.getLongArray(key);
            final long[] tally = new long[STAT_COUNT];
            System.arraycopy(saved, 0, tally, 0, Math.min(saved.length, STAT_COUNT));
            wars.stats.put(Long.parseLong(key), tally);
        }
        final long[] lockDefenders = tag.getLongArray("lockDefenders");
        final long[] lockAttackers = tag.getLongArray("lockAttackers");
        final long[] lockUntil = tag.getLongArray("lockUntil");
        for (int i = 0; i < Math.min(lockDefenders.length, Math.min(lockAttackers.length, lockUntil.length)); i++)
        {
            wars.locks.put((int) lockDefenders[i], new long[] {lockAttackers[i], lockUntil[i]});
        }
        final long[] repelledKeys = tag.getLongArray("repelledKeys");
        final int[] repelledCounts = tag.getIntArray("repelledCounts");
        for (int i = 0; i < Math.min(repelledKeys.length, repelledCounts.length); i++)
        {
            wars.repelled.put(repelledKeys[i], repelledCounts[i]);
        }
        final long[] endKeys = tag.getLongArray("endKeys");
        final long[] endTimes = tag.getLongArray("endTimes");
        for (int i = 0; i < Math.min(endKeys.length, endTimes.length); i++)
        {
            wars.lastAssaultEnd.put(endKeys[i], endTimes[i]);
        }
        return wars;
    }
}
