package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: store the player's whole main inventory (hotbar kept) in the warehouse.
 */
public record TerminalDepositPayload(BlockPos warehouse) implements CustomPacketPayload
{
    public static final Type<TerminalDepositPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "terminal_deposit"));

    public static final StreamCodec<ByteBuf, TerminalDepositPayload> STREAM_CODEC =
      BlockPos.STREAM_CODEC.map(TerminalDepositPayload::new, TerminalDepositPayload::warehouse);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
