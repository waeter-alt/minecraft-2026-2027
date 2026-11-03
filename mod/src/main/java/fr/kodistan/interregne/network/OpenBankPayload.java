package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: open (or refresh) the bank counter window with the player's balance and their colony's treasury
 * (empty colony name when the player has no colony).
 */
public record OpenBankPayload(BlockPos counter, long balance, double feeRate, String colonyName, long treasury) implements CustomPacketPayload
{
    public static final Type<OpenBankPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "open_bank"));

    public static final StreamCodec<ByteBuf, OpenBankPayload> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC, OpenBankPayload::counter,
      ByteBufCodecs.VAR_LONG, OpenBankPayload::balance,
      ByteBufCodecs.DOUBLE, OpenBankPayload::feeRate,
      ByteBufCodecs.STRING_UTF8, OpenBankPayload::colonyName,
      ByteBufCodecs.VAR_LONG, OpenBankPayload::treasury,
      OpenBankPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
