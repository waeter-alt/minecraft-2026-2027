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
 * send to war, the Influence a declaration costs, and the couronnes tribute a capitulation costs against what its treasury holds.
 * Aide de clan: the price of one soldier of a contingent and the most a contingent holds.
 */
public record DiplomacySyncPayload(int colony, int legionAvailable, int archersAvailable, int warCost, long tribute, long treasury, long aidPrice, int aidMax,
                                   List<Entry> entries) implements CustomPacketPayload
{
    public DiplomacySyncPayload(final int colony, final int legionAvailable, final int archersAvailable, final int warCost, final long tribute, final long treasury,
                                final List<Entry> entries)
    {
        this(colony, legionAvailable, archersAvailable, warCost, tribute, treasury, 150L, 10, entries);
    }

    public static final int ROAD_NONE = 0;
    public static final int ROAD_BUILDING = 1;
    public static final int ROAD_READY = 2;
    public static final int ROAD_IMPOSSIBLE = 3;

    public static final int CAMPAIGN_NONE = 0;
    public static final int CAMPAIGN_MARCHING = 1;
    public static final int CAMPAIGN_ASSAULTING = 2;

    /**
     * {@code aidOut}: soldiers this colony keeps in reserve for that one; {@code aidIn}: the other way round.
     * {@code engagedOut} / {@code engagedIn}: soldiers of that contingent still standing while it is out, -1 when it is not.
     * {@code surrenderSeconds}: for an open war that colony wages on this one, the seconds before this one may demand its surrender
     * (see {@link #SURRENDER_NO}). {@code lockHolder} / {@code lockSeconds}: for an open war this colony wages on that one, the other colony
     * that has it reserved for its assaults and for how many seconds yet ({@code ""} and 0 when none).
     */
    public record Entry(int id, String name, String owner, int citizens, int x, int z, int status, int noticeSeconds, int roadState, int campaign,
                        int aidOut, int aidIn, int engagedOut, int engagedIn, int surrenderSeconds, String lockHolder, int lockSeconds)
    {
        public Entry(final int id, final String name, final String owner, final int citizens, final int x, final int z, final int status, final int noticeSeconds,
                     final int roadState, final int campaign, final int aidOut, final int aidIn, final int engagedOut, final int engagedIn)
        {
            this(id, name, owner, citizens, x, z, status, noticeSeconds, roadState, campaign, aidOut, aidIn, engagedOut, engagedIn, SURRENDER_NO, "", 0);
        }

        public Entry(final int id, final String name, final String owner, final int citizens, final int x, final int z, final int status, final int noticeSeconds,
                     final int roadState, final int campaign)
        {
            this(id, name, owner, citizens, x, z, status, noticeSeconds, roadState, campaign, 0, 0, -1, -1);
        }
    }

    /** {@link Entry#surrenderSeconds}: nothing to wait for, the attacker's troops are out (or there is no open war against this colony). 0 means the surrender may be demanded now. */
    public static final int SURRENDER_NO = -1;

    public static final Type<DiplomacySyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "diplomacy_sync"));

    public static final StreamCodec<ByteBuf, DiplomacySyncPayload> STREAM_CODEC = StreamCodec.of((buf, payload) -> {
        ByteBufCodecs.VAR_INT.encode(buf, payload.colony());
        ByteBufCodecs.VAR_INT.encode(buf, payload.legionAvailable());
        ByteBufCodecs.VAR_INT.encode(buf, payload.archersAvailable());
        ByteBufCodecs.VAR_INT.encode(buf, payload.warCost());
        ByteBufCodecs.VAR_LONG.encode(buf, payload.tribute());
        ByteBufCodecs.VAR_LONG.encode(buf, payload.treasury());
        ByteBufCodecs.VAR_LONG.encode(buf, payload.aidPrice());
        ByteBufCodecs.VAR_INT.encode(buf, payload.aidMax());
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
            ByteBufCodecs.VAR_INT.encode(buf, entry.aidOut());
            ByteBufCodecs.VAR_INT.encode(buf, entry.aidIn());
            ByteBufCodecs.INT.encode(buf, entry.engagedOut());
            ByteBufCodecs.INT.encode(buf, entry.engagedIn());
            ByteBufCodecs.INT.encode(buf, entry.surrenderSeconds());
            ByteBufCodecs.STRING_UTF8.encode(buf, entry.lockHolder());
            ByteBufCodecs.VAR_INT.encode(buf, entry.lockSeconds());
        }
    }, buf -> {
        final int colony = ByteBufCodecs.VAR_INT.decode(buf);
        final int legion = ByteBufCodecs.VAR_INT.decode(buf);
        final int archers = ByteBufCodecs.VAR_INT.decode(buf);
        final int warCost = ByteBufCodecs.VAR_INT.decode(buf);
        final long tribute = ByteBufCodecs.VAR_LONG.decode(buf);
        final long treasury = ByteBufCodecs.VAR_LONG.decode(buf);
        final long aidPrice = ByteBufCodecs.VAR_LONG.decode(buf);
        final int aidMax = ByteBufCodecs.VAR_INT.decode(buf);
        final int count = ByteBufCodecs.VAR_INT.decode(buf);
        final List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            entries.add(new Entry(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
              ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.INT.decode(buf), ByteBufCodecs.INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
              ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
              ByteBufCodecs.INT.decode(buf), ByteBufCodecs.INT.decode(buf), ByteBufCodecs.INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
              ByteBufCodecs.VAR_INT.decode(buf)));
        }
        return new DiplomacySyncPayload(colony, legion, archers, warCost, tribute, treasury, aidPrice, aidMax, entries);
    });

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
