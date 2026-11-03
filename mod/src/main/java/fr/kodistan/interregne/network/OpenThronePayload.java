package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: open the Trône impérial window. Intendants get the edict controls; everyone sees the proclamation.
 */
public record OpenThronePayload(BlockPos throne, boolean intendant, String state, String colonyLine, long treasury) implements CustomPacketPayload
{
    public static final Type<OpenThronePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "open_throne"));

    public static final StreamCodec<ByteBuf, OpenThronePayload> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC, OpenThronePayload::throne,
      ByteBufCodecs.BOOL, OpenThronePayload::intendant,
      ByteBufCodecs.STRING_UTF8, OpenThronePayload::state,
      ByteBufCodecs.STRING_UTF8, OpenThronePayload::colonyLine,
      ByteBufCodecs.VAR_LONG, OpenThronePayload::treasury,
      OpenThronePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
