package fr.kodistan.interregne.routes;

import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client: what the road screen of a Poste commercial shows.
 *
 * @param level    the road's level, 1 to {@link RoadLevels#MAX}
 * @param underWay works are going on: nothing can be ordered until they end
 * @param cost     what the next level costs the player (0 in creative), -1 at the top or when none is configured
 * @param seconds  how long the works take
 * @param notice   the server's answer to the last action (a refusal), or empty
 * @param perks    what a road of each level gives walkers, index 0 for level 1
 */
public record RoadStatePayload(BlockPos post, String from, String to, int length, int level, boolean underWay, long cost, long available, int seconds,
                               String notice, List<String> perks) implements CustomPacketPayload
{
    public static final Type<RoadStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "road_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RoadStatePayload> STREAM_CODEC = StreamCodec.of(RoadStatePayload::write, RoadStatePayload::read);

    private static void write(final RegistryFriendlyByteBuf buf, final RoadStatePayload p)
    {
        BlockPos.STREAM_CODEC.encode(buf, p.post);
        buf.writeUtf(p.from);
        buf.writeUtf(p.to);
        buf.writeVarInt(p.length);
        buf.writeVarInt(p.level);
        buf.writeBoolean(p.underWay);
        buf.writeVarLong(p.cost + 1);
        buf.writeVarLong(p.available);
        buf.writeVarInt(p.seconds);
        buf.writeUtf(p.notice);
        buf.writeVarInt(p.perks.size());
        for (final String perk : p.perks)
        {
            buf.writeUtf(perk);
        }
    }

    private static RoadStatePayload read(final RegistryFriendlyByteBuf buf)
    {
        final BlockPos post = BlockPos.STREAM_CODEC.decode(buf);
        final String from = buf.readUtf();
        final String to = buf.readUtf();
        final int length = buf.readVarInt();
        final int level = buf.readVarInt();
        final boolean underWay = buf.readBoolean();
        final long cost = buf.readVarLong() - 1;
        final long available = buf.readVarLong();
        final int seconds = buf.readVarInt();
        final String notice = buf.readUtf();
        final List<String> perks = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--)
        {
            perks.add(buf.readUtf());
        }
        return new RoadStatePayload(post, from, to, length, level, underWay, cost, available, seconds, notice, perks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
