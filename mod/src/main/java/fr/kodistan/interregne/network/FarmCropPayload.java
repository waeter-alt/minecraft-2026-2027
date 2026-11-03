package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the crop chosen for a farm about to be built or upgraded; an empty crop cancels the construction.
 */
public record FarmCropPayload(BlockPos hut, String crop) implements CustomPacketPayload
{
    public static final Type<FarmCropPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "farm_crop"));

    public static final StreamCodec<ByteBuf, FarmCropPayload> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC, FarmCropPayload::hut,
      ByteBufCodecs.STRING_UTF8, FarmCropPayload::crop,
      FarmCropPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
