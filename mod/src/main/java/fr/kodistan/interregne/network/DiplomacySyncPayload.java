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
 * {@code Alliances.NONE/ALLIED/SENT/RECEIVED}), for the town hall's Diplomatie page. Also the guards {@code colony} can
 * send to war and the Influence a declaration costs.
 */
public record DiplomacySyncPayload(int colony, int legionAvailable, int archersAvailable, int warCost, List<Entry> entries) implements CustomPacketPayload
{
    public static final int ROAD_NONE = 0;
    public static final int ROAD_BUILDING = 1;
    public static final int ROAD_READY = 2;
    public static final int ROAD_IMPOSSIBLE = 3;

    public static final int CAMPAIGN_NONE = 0;
    public static final int CAMPAIGN_MARCHING = 1;
    public static final int CAMPAIGN_ASSAULTING = 2;

    public record Entry(int id, String name, String owner, int citizens, int x, int z, int status, int noticeSeconds, int roadState, int campaign)
    {
    }

    public static final Type<DiplomacySyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "diplomacy_sync"));

    public static final StreamCodec<ByteBuf, DiplomacySyncPayload> STREAM_CODEC = StreamCodec.of((buf, payload) -> {
        ByteBufCodecs.VAR_INT.encode(buf, payload.colony());
        ByteBufCodecs.VAR_INT.encode(buf, payload.legionAvailable());
        ByteBufCodecs.VAR_INT.encode(buf, payload.archersAvailable());
        ByteBufCodecs.VAR_INT.encode(buf, payload.warCost());
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
            ByteBufCodecs.VAR_INT.encode(buf, entry.noticeSeconds());
            ByteBufCodecs.VAR_INT.encode(buf, entry.roadState());
            ByteBufCodecs.VAR_INT.encode(buf, entry.campaign());
        }
    }, buf -> {
        final int colony = ByteBufCodecs.VAR_INT.decode(buf);
        final int legion = ByteBufCodecs.VAR_INT.decode(buf);
        final int archers = ByteBufCodecs.VAR_INT.decode(buf);
        final int warCost = ByteBufCodecs.VAR_INT.decode(buf);
        final int count = ByteBufCodecs.VAR_INT.decode(buf);
        final List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            entries.add(new Entry(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
              ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.INT.decode(buf), ByteBufCodecs.INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
              ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));
        }
        return new DiplomacySyncPayload(colony, legion, archers, warCost, entries);
    });

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
