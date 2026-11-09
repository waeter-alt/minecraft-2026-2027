package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: "Tout reconstruire" clicked from the town hall screen, for this colony. */
public record WarRebuildPayload(int colony) implements CustomPacketPayload
{
    public static final Type<WarRebuildPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "war_rebuild"));

    public static final StreamCodec<ByteBuf, WarRebuildPayload> STREAM_CODEC =
      StreamCodec.composite(ByteBufCodecs.VAR_INT, WarRebuildPayload::colony, WarRebuildPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
