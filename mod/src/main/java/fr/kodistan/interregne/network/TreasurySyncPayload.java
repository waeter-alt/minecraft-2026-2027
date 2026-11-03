package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: a colony's regional treasury balance, sent to its members when it changes and when they log in,
 * so the hut build window can count it without asking.
 */
public record TreasurySyncPayload(String colonyKey, long balance) implements CustomPacketPayload
{
    public static final Type<TreasurySyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "treasury_sync"));

    public static final StreamCodec<ByteBuf, TreasurySyncPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.STRING_UTF8, TreasurySyncPayload::colonyKey,
      ByteBufCodecs.VAR_LONG, TreasurySyncPayload::balance,
      TreasurySyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
