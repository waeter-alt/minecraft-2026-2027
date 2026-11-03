package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: ask which crop a farm grows, just before its construction or upgrade starts.
 */
public record OpenFarmCropPayload(BlockPos hut, String current, boolean upgrade) implements CustomPacketPayload
{
    public static final Type<OpenFarmCropPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "open_farm_crop"));

    public static final StreamCodec<ByteBuf, OpenFarmCropPayload> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC, OpenFarmCropPayload::hut,
      ByteBufCodecs.STRING_UTF8, OpenFarmCropPayload::current,
      ByteBufCodecs.BOOL, OpenFarmCropPayload::upgrade,
      OpenFarmCropPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
