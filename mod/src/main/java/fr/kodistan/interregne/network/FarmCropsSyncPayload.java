package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: the crops grown by the player's colony farms, which its scarecrow fields may be sown with.
 */
public record FarmCropsSyncPayload(List<String> crops) implements CustomPacketPayload
{
    public static final Type<FarmCropsSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "farm_crops"));

    public static final StreamCodec<ByteBuf, FarmCropsSyncPayload> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).map(FarmCropsSyncPayload::new, FarmCropsSyncPayload::crops);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
