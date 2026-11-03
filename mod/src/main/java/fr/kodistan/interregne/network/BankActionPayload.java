package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: an operation at a bank counter. The amount only matters for a withdrawal.
 */
public record BankActionPayload(BlockPos counter, int action, long amount) implements CustomPacketPayload
{
    /**
     * Put every Couronne carried in the player's account.
     */
    public static final int DEPOSIT = 0;

    /**
     * Take an amount from the player's account, fee charged.
     */
    public static final int WITHDRAW = 1;

    /**
     * Give every Couronne carried to the player's colony treasury.
     */
    public static final int FUND_TREASURY = 2;

    public static final Type<BankActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "bank_action"));

    public static final StreamCodec<ByteBuf, BankActionPayload> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC, BankActionPayload::counter,
      ByteBufCodecs.VAR_INT, BankActionPayload::action,
      ByteBufCodecs.VAR_LONG, BankActionPayload::amount,
      BankActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
