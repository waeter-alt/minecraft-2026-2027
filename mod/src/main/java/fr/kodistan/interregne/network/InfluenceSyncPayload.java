package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the player's Influence and the change that produced it (0 for a plain refresh).
 */
public record InfluenceSyncPayload(long influence, long delta) implements CustomPacketPayload
{
    public static final Type<InfluenceSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "influence_sync"));

    public static final StreamCodec<ByteBuf, InfluenceSyncPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_LONG, InfluenceSyncPayload::influence,
      ByteBufCodecs.VAR_LONG, InfluenceSyncPayload::delta,
      InfluenceSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
