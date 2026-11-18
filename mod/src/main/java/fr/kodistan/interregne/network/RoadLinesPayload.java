package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: the centre lines of every trade road built, so the wall planner can show where a wall would
 * cross one. Flat, per road: colonyA, colonyB, cell count, then x, z per cell.
 */
public record RoadLinesPayload(List<Integer> data) implements CustomPacketPayload
{
    public static final Type<RoadLinesPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "road_lines"));

    private static final StreamCodec<ByteBuf, List<Integer>> DATA = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(1 << 20));

    public static final StreamCodec<ByteBuf, RoadLinesPayload> STREAM_CODEC = StreamCodec.of((buf, payload) -> DATA.encode(buf, payload.data()),
      buf -> new RoadLinesPayload(DATA.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
