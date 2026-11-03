package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: an operation at a colony's Banque régionale. The amount only matters for a withdrawal.
 */
public record RegionalBankActionPayload(BlockPos bank, int action, long amount) implements CustomPacketPayload
{
    /**
     * Put every Couronne carried in the colony treasury, up to its capacity.
     */
    public static final int DEPOSIT = 0;

    /**
     * Take an amount from the treasury (owner and officers only).
     */
    public static final int WITHDRAW = 1;

    /**
     * Raise the bank one level, which raises the treasury's capacity.
     */
    public static final int UPGRADE = 2;

    public static final Type<RegionalBankActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "regional_bank_action"));

    public static final StreamCodec<ByteBuf, RegionalBankActionPayload> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC, RegionalBankActionPayload::bank,
      ByteBufCodecs.VAR_INT, RegionalBankActionPayload::action,
      ByteBufCodecs.VAR_LONG, RegionalBankActionPayload::amount,
      RegionalBankActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
