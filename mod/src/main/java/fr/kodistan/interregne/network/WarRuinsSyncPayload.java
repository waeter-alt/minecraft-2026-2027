package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: one colony's war-ruin count and the price to rebuild all of them, plus whether its wall has a
 * war breach open and the flat price to fix it, so the town hall screen can show the "Tout reconstruire" button
 * (key as RegionalTreasury.keyOf) covering both.
 */
public record WarRuinsSyncPayload(String colony, int count, long price, boolean wallDamaged, long wallPrice) implements CustomPacketPayload
{
    public static final Type<WarRuinsSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "war_ruins_sync"));

    public static final StreamCodec<ByteBuf, WarRuinsSyncPayload> STREAM_CODEC = StreamCodec.of((buf, payload) -> {
        ByteBufCodecs.STRING_UTF8.encode(buf, payload.colony());
        ByteBufCodecs.VAR_INT.encode(buf, payload.count());
        ByteBufCodecs.VAR_LONG.encode(buf, payload.price());
        ByteBufCodecs.BOOL.encode(buf, payload.wallDamaged());
        ByteBufCodecs.VAR_LONG.encode(buf, payload.wallPrice());
    }, buf -> new WarRuinsSyncPayload(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
      ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
