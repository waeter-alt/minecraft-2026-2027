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

    public boolean end(final int attacker, final int defender)
    {
        opened.remove(key(attacker, defender));
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
        return wars;
    }
}
