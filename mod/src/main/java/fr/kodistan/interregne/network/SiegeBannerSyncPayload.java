package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to every client: all the siege banners standing (or just fallen) in the overworld, so each client draws their
 * beam, capture ring and screen marker even far beyond its render distance.
 */
public record SiegeBannerSyncPayload(List<Entry> banners) implements CustomPacketPayload
{
    public static final byte STANDING  = 0;
    public static final byte CAPTURING = 1;
    public static final byte CONTESTED = 2;
    public static final byte FALLEN    = 3;

    /**
     * @param defender the attacked colony's id (one banner per assault)
     * @param color    the attacker's colony colour, RGB
     * @param progress how far the flag came down, 0..1
     * @param radius   the capture zone's radius, in blocks
     */
    public record Entry(int defender, BlockPos pos, int color, float progress, byte state, int radius, String attacker, String target) {}

    public static final Type<SiegeBannerSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "siege_banners"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SiegeBannerSyncPayload> STREAM_CODEC = StreamCodec.of((buf, payload) -> {
        buf.writeVarInt(payload.banners().size());
        for (final Entry e : payload.banners())
        {
            buf.writeVarInt(e.defender());
            buf.writeBlockPos(e.pos());
            buf.writeInt(e.color());
            buf.writeFloat(e.progress());
            buf.writeByte(e.state());
            buf.writeVarInt(e.radius());
            ByteBufCodecs.STRING_UTF8.encode(buf, e.attacker());
            ByteBufCodecs.STRING_UTF8.encode(buf, e.target());
        }
    }, buf -> {
        final int n = buf.readVarInt();
        final List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
        {
            list.add(new Entry(buf.readVarInt(), buf.readBlockPos(), buf.readInt(), buf.readFloat(), buf.readByte(), buf.readVarInt(),
              ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf)));
        }
        return new SiegeBannerSyncPayload(list);
    });

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
