package fr.kodistan.interregne.politics;

import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.network.InfluenceSyncPayload;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Political weight of each player, saved with the world. Never negative.
 * Minimal API for now: other systems (diplomacy, elections, deaths) read and adjust it through the static helpers.
 */
public final class Influence extends SavedData
{
    private static final String NAME = "interregne_influence";

    private final Map<UUID, Long> points = new HashMap<>();

    private static Influence data(final MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Influence::new, Influence::load, null), NAME);
    }

    /**
     * A player's current Influence.
     */
    public static long get(final MinecraftServer server, final UUID player)
    {
        return data(server).points.getOrDefault(player, 0L);
    }

    /**
     * Grant Influence (a negative amount removes it, down to zero).
     *
     * @return the new Influence.
     */
    public static long add(final MinecraftServer server, final UUID player, final long amount, final String reason)
    {
        final Influence data = data(server);
        final long before = data.points.getOrDefault(player, 0L);
        final long after = Math.max(0, before + amount);
        if (after != before)
        {
            data.points.put(player, after);
            data.setDirty();
            InterregneMod.LOGGER.info("Influence of {}: {} -> {} ({})", player, before, after, reason);
            final ServerPlayer online = server.getPlayerList().getPlayer(player);
            if (online != null)
            {
                PacketDistributor.sendToPlayer(online, new InfluenceSyncPayload(after, after - before));
            }
        }
        return after;
    }

    /**
     * Send a player their Influence, e.g. on login.
     */
    public static void sync(final ServerPlayer player)
    {
        PacketDistributor.sendToPlayer(player, new InfluenceSyncPayload(get(player.server, player.getUUID()), 0));
    }

    /**
     * Every player's Influence, highest first.
     */
    public static List<Map.Entry<UUID, Long>> ranking(final MinecraftServer server)
    {
        return data(server).points.entrySet().stream()
                 .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                 .toList();
    }

    /**
     * Take Influence away, never going below zero.
     *
     * @return how much was actually removed.
     */
    public static long remove(final MinecraftServer server, final UUID player, final long amount, final String reason)
    {
        final long before = get(server, player);
        return before - add(server, player, -Math.max(0, amount), reason);
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider provider)
    {
        final CompoundTag list = new CompoundTag();
        points.forEach((player, amount) -> list.putLong(player.toString(), amount));
        tag.put("points", list);
        return tag;
    }

    private static Influence load(final CompoundTag tag, final HolderLookup.Provider provider)
    {
        final Influence data = new Influence();
        final CompoundTag list = tag.getCompound("points");
        for (final String key : list.getAllKeys())
        {
            data.points.put(UUID.fromString(key), list.getLong(key));
        }
        return data;
    }
}
