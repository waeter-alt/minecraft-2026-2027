package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Client to server: take this many of an item out of the warehouse.
 */
public record TerminalWithdrawPayload(BlockPos warehouse, ItemStack item, int amount) implements CustomPacketPayload
{
    public static final Type<TerminalWithdrawPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "terminal_withdraw"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalWithdrawPayload> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC, TerminalWithdrawPayload::warehouse,
      ItemStack.STREAM_CODEC, TerminalWithdrawPayload::item,
      ByteBufCodecs.VAR_INT, TerminalWithdrawPayload::amount,
      TerminalWithdrawPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
