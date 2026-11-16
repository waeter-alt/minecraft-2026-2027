package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client: every other colony of the dimension with the tie it has to {@code colony} (see
 * {@code Alliances.NONE/ALLIED/SENT/RECEIVED}), for the town hall's Diplomatie page.
 */
public record DiplomacySyncPayload(int colony, List<Entry> entries) implements CustomPacketPayload
{
    public record Entry(int id, String name, String owner, int citizens, int x, int z, int status)
    {
    }

    public static final Type<DiplomacySyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "diplomacy_sync"));

    public static final StreamCodec<ByteBuf, DiplomacySyncPayload> STREAM_CODEC = StreamCodec.of((buf, payload) -> {
        ByteBufCodecs.VAR_INT.encode(buf, payload.colony());
        ByteBufCodecs.VAR_INT.encode(buf, payload.entries().size());
        for (final Entry entry : payload.entries())
        {
            ByteBufCodecs.VAR_INT.encode(buf, entry.id());
            ByteBufCodecs.STRING_UTF8.encode(buf, entry.name());
            ByteBufCodecs.STRING_UTF8.encode(buf, entry.owner());
            ByteBufCodecs.VAR_INT.encode(buf, entry.citizens());
            ByteBufCodecs.INT.encode(buf, entry.x());
            ByteBufCodecs.INT.encode(buf, entry.z());
            ByteBufCodecs.VAR_INT.encode(buf, entry.status());
        }
    }, buf -> {
        final int colony = ByteBufCodecs.VAR_INT.decode(buf);
        final int count = ByteBufCodecs.VAR_INT.decode(buf);
        final List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            entries.add(new Entry(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
              ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.INT.decode(buf), ByteBufCodecs.INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));
        }
        return new DiplomacySyncPayload(colony, entries);
    });

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
