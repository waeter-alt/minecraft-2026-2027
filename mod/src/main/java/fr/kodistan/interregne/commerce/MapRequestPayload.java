package fr.kodistan.interregne.commerce;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the kingdom map is open and wants fresh positions. It carries nothing, the answer is the same for everyone.
 */
public record MapRequestPayload() implements CustomPacketPayload
{
    public static final Type<MapRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "map_request"));

    public static final StreamCodec<ByteBuf, MapRequestPayload> STREAM_CODEC = StreamCodec.unit(new MapRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
