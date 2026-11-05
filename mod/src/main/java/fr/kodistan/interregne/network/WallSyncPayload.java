package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: one colony's wall (key as RegionalTreasury.keyOf), so windows can show it. points and gates are flat x, z pairs.
 */
public record WallSyncPayload(String colony, List<Integer> points, List<Integer> gates, boolean closed, int level, boolean building, long refund)
  implements CustomPacketPayload
{
    public static final Type<WallSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "wall_sync"));

    private static final StreamCodec<ByteBuf, List<Integer>> POINTS = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(512));
    private static final StreamCodec<ByteBuf, List<Integer>> GATES = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64));

    /**
     * refund: the Couronnes a demolition gives back to the colony.
     */
    public static final StreamCodec<ByteBuf, WallSyncPayload> STREAM_CODEC = StreamCodec.of((buf, payload) -> {
        ByteBufCodecs.STRING_UTF8.encode(buf, payload.colony());
        POINTS.encode(buf, payload.points());
        GATES.encode(buf, payload.gates());
        ByteBufCodecs.BOOL.encode(buf, payload.closed());
        ByteBufCodecs.VAR_INT.encode(buf, payload.level());
        ByteBufCodecs.BOOL.encode(buf, payload.building());
        ByteBufCodecs.VAR_LONG.encode(buf, payload.refund());
    }, buf -> new WallSyncPayload(ByteBufCodecs.STRING_UTF8.decode(buf), POINTS.decode(buf), GATES.decode(buf), ByteBufCodecs.BOOL.decode(buf),
      ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
