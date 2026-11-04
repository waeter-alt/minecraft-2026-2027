package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Exactly what the Intendance would settle for a colony, row by row, as the server counts it: the citizens it would
 * tend and the supplies it would deliver, each with its price, then each card's total in Couronnes and Influence.
 * What the colony already handles itself is not in it, so the page never shows it.
 */
public record IntendanceQuotePayload(int colony, List<Care> care, long careGold, long careInfluence, List<Supply> supplies, long supplyGold,
                                     long supplyInfluence) implements CustomPacketPayload
{
    /**
     * A citizen the Intendance would cure (sick) or feed.
     */
    public record Care(int citizen, String name, boolean sick, int cost)
    {
        static final StreamCodec<RegistryFriendlyByteBuf, Care> CODEC = StreamCodec.composite(
          ByteBufCodecs.VAR_INT, Care::citizen,
          ByteBufCodecs.STRING_UTF8, Care::name,
          ByteBufCodecs.BOOL, Care::sick,
          ByteBufCodecs.VAR_INT, Care::cost,
          Care::new);
    }

    /**
     * An item a worker waits on and nobody in the colony provides; cost -1 when the Intendance never sells it.
     */
    public record Supply(ItemStack stack, Component forWhom, int cost)
    {
        static final StreamCodec<RegistryFriendlyByteBuf, Supply> CODEC = StreamCodec.composite(
          ItemStack.OPTIONAL_STREAM_CODEC, Supply::stack,
          ComponentSerialization.TRUSTED_STREAM_CODEC, Supply::forWhom,
          ByteBufCodecs.INT, Supply::cost,
          Supply::new);

        public boolean rare()
        {
            return cost < 0;
        }
    }

    public static final Type<IntendanceQuotePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "intendance_quote"));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<Care>>   CARE_LIST   = Care.CODEC.apply(ByteBufCodecs.list());
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Supply>> SUPPLY_LIST = Supply.CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, IntendanceQuotePayload> STREAM_CODEC = StreamCodec.of(
      (buf, quote) -> {
          ByteBufCodecs.VAR_INT.encode(buf, quote.colony());
          CARE_LIST.encode(buf, quote.care());
          ByteBufCodecs.VAR_LONG.encode(buf, quote.careGold());
          ByteBufCodecs.VAR_LONG.encode(buf, quote.careInfluence());
          SUPPLY_LIST.encode(buf, quote.supplies());
          ByteBufCodecs.VAR_LONG.encode(buf, quote.supplyGold());
          ByteBufCodecs.VAR_LONG.encode(buf, quote.supplyInfluence());
      },
      buf -> new IntendanceQuotePayload(ByteBufCodecs.VAR_INT.decode(buf), CARE_LIST.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
        ByteBufCodecs.VAR_LONG.decode(buf), SUPPLY_LIST.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
