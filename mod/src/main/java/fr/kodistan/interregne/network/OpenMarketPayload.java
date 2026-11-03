package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: open (or refresh) a Marché impérial's window. Arrays are indexed by crate ordinal.
 */
public record OpenMarketPayload(BlockPos market, int[] buy, int[] sell, int[] stock, int[] carried, long purse) implements CustomPacketPayload
{
    public static final Type<OpenMarketPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "open_market"));

    public static final StreamCodec<FriendlyByteBuf, OpenMarketPayload> STREAM_CODEC = StreamCodec.ofMember(OpenMarketPayload::write, OpenMarketPayload::read);

    private static OpenMarketPayload read(final FriendlyByteBuf buf)
    {
        return new OpenMarketPayload(buf.readBlockPos(), buf.readVarIntArray(), buf.readVarIntArray(), buf.readVarIntArray(), buf.readVarIntArray(), buf.readVarLong());
    }

    private void write(final FriendlyByteBuf buf)
    {
        buf.writeBlockPos(market);
        buf.writeVarIntArray(buy);
        buf.writeVarIntArray(sell);
        buf.writeVarIntArray(stock);
        buf.writeVarIntArray(carried);
        buf.writeVarLong(purse);
    }

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
