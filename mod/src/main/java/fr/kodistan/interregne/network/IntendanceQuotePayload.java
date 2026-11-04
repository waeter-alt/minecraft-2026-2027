package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The Intendance's prices for a colony, as the server counts them: the sick and hungry it can tend, the supplies it
 * can buy, and what each card costs in Couronnes and Influence.
 */
public record IntendanceQuotePayload(int colony, int sick, int hungry, long careGold, long careInfluence, int supplies, int strategic, long supplyGold,
                                     long supplyInfluence) implements CustomPacketPayload
{
    public static final Type<IntendanceQuotePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "intendance_quote"));

    public static final StreamCodec<ByteBuf, IntendanceQuotePayload> STREAM_CODEC = StreamCodec.of(
      (buf, quote) -> {
          ByteBufCodecs.VAR_INT.encode(buf, quote.colony());
          ByteBufCodecs.VAR_INT.encode(buf, quote.sick());
          ByteBufCodecs.VAR_INT.encode(buf, quote.hungry());
          ByteBufCodecs.VAR_LONG.encode(buf, quote.careGold());
          ByteBufCodecs.VAR_LONG.encode(buf, quote.careInfluence());
          ByteBufCodecs.VAR_INT.encode(buf, quote.supplies());
          ByteBufCodecs.VAR_INT.encode(buf, quote.strategic());
          ByteBufCodecs.VAR_LONG.encode(buf, quote.supplyGold());
          ByteBufCodecs.VAR_LONG.encode(buf, quote.supplyInfluence());
      },
      buf -> new IntendanceQuotePayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
        ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
        ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
