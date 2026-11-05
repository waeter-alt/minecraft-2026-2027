package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: tear the colony's wall down, gates and all.
 */
public record WallDemolishPayload(int colony) implements CustomPacketPayload
{
    public static final Type<WallDemolishPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "wall_demolish"));

    public static final StreamCodec<ByteBuf, WallDemolishPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, WallDemolishPayload::colony,
      WallDemolishPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
