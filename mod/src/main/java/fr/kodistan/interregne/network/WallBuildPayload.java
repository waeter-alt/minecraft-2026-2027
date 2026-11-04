package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Client to server: build this traced wall for the colony (x and z of each point, in order).
 */
public record WallBuildPayload(int colony, List<Integer> xs, List<Integer> zs, boolean closed) implements CustomPacketPayload
{
    public static final Type<WallBuildPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "wall_build"));

    public static final StreamCodec<ByteBuf, WallBuildPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, WallBuildPayload::colony,
      ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(256)), WallBuildPayload::xs,
      ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(256)), WallBuildPayload::zs,
      ByteBufCodecs.BOOL, WallBuildPayload::closed,
      WallBuildPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
