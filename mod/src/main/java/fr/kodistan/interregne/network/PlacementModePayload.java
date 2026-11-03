package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the build tool mode ("build now" or "plan only"), sent right before Structurize's placement message.
 */
public record PlacementModePayload(boolean buildNow) implements CustomPacketPayload
{
    public static final Type<PlacementModePayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "placement_mode"));

    public static final StreamCodec<ByteBuf, PlacementModePayload> STREAM_CODEC =
      ByteBufCodecs.BOOL.map(PlacementModePayload::new, PlacementModePayload::buildNow);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
