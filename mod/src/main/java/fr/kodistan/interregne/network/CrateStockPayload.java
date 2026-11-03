package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: the crates stored in the player's colony warehouse, one count per crate type in enum order.
 */
public record CrateStockPayload(List<Integer> counts) implements CustomPacketPayload
{
    public static final Type<CrateStockPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "crate_stock"));

    public static final StreamCodec<ByteBuf, CrateStockPayload> STREAM_CODEC =
      ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).map(CrateStockPayload::new, CrateStockPayload::counts);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
