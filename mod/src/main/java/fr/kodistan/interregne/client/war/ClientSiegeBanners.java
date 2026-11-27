package fr.kodistan.interregne.client.war;

import fr.kodistan.interregne.network.SiegeBannerSyncPayload;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The siege banners the server last told about, with the client time each one fell (for the fade-out). Shown progress
 * eases toward the server's value so the ring fills smoothly between the half-second updates.
 */
public final class ClientSiegeBanners
{
    public static final class Shown
    {
        public SiegeBannerSyncPayload.Entry entry;
        public float progress;
        public long fellAt = -1;

        Shown(final SiegeBannerSyncPayload.Entry entry)
        {
            this.entry = entry;
            this.progress = entry.progress();
        }
    }

    private static final Map<Integer, Shown> BANNERS = new HashMap<>();

    private ClientSiegeBanners()
    {
    }

    public static void put(final SiegeBannerSyncPayload payload)
    {
        final Map<Integer, Shown> next = new HashMap<>();
        for (final SiegeBannerSyncPayload.Entry e : payload.banners())
        {
            Shown shown = BANNERS.get(e.defender());
            if (shown == null || !shown.entry.pos().equals(e.pos()))
            {
                shown = new Shown(e);
            }
            if (e.state() == SiegeBannerSyncPayload.FALLEN && shown.fellAt < 0)
            {
                shown.fellAt = System.currentTimeMillis();
            }
            shown.entry = e;
            next.put(e.defender(), shown);
        }
        BANNERS.clear();
        BANNERS.putAll(next);
    }

    public static Iterable<Shown> all()
    {
        return BANNERS.values();
    }

    public static boolean isEmpty()
    {
        return BANNERS.isEmpty();
    }

    /** Called once per frame by the renderer. */
    static void ease(final float frameSeconds)
    {
        for (final Shown s : BANNERS.values())
        {
            s.progress += (s.entry.progress() - s.progress) * Math.min(1F, frameSeconds * 4F);
        }
    }

    /** For UiShot and dev scenarios: show these banners as if the server sent them. */
    public static void fake(final List<SiegeBannerSyncPayload.Entry> entries)
    {
        put(new SiegeBannerSyncPayload(entries));
    }

    public static void clear()
    {
        BANNERS.clear();
    }
}
