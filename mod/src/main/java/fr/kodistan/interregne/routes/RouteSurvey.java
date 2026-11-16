package fr.kodistan.interregne.routes;

import fr.kodistan.interregne.routes.plan.HeightField;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Surveys the country between two points into a {@link HeightField}: a band around the straight line, wide enough to
 * go round what stands in the way. A route between two distant colonies crosses land nobody is standing on; that
 * must not make it impossible (it used to) nor freeze the server. So, for each chunk of the band:
 * <ul>
 * <li>loaded: read where it stands;</li>
 * <li>saved: read from its region file on worker threads, without loading it into the world (loading costs the
 * server thread some ten times more, and a band is thousands of chunks);</li>
 * <li>never generated: asked from the chunk system, which generates it without blocking the tick, read, let go.</li>
 * </ul>
 */
public final class RouteSurvey
{
    /** Holds a chunk while it is generated and read; expires by itself if the survey is dropped. */
    private static final TicketType<ChunkPos> TICKET = TicketType.create("interregne_route_survey", Comparator.comparingLong(ChunkPos::toLong), 20 * 60);
    private static final int MAX_READING = 192;
    private static final int MAX_LOADING = 48;
    private static final long TICK_BUDGET_NANOS = 8_000_000L;
    private static final int CHUNK_MARGIN = 12;

    private record Reading(ChunkPos pos, CompletableFuture<RouteProbe.Columns> future)
    {
    }

    private record Loading(ChunkPos pos, CompletableFuture<ChunkResult<ChunkAccess>> future)
    {
    }

    private final ServerLevel level;
    private final HeightField field;
    private final Deque<ChunkPos> todo = new ArrayDeque<>();
    private final Deque<ChunkPos> unsaved = new ArrayDeque<>();
    private final List<Reading> reading = new ArrayList<>();
    private final List<Loading> loading = new ArrayList<>();
    private final int total;
    private int done;
    private int generated;

    public RouteSurvey(final ServerLevel level, final BlockPos a, final BlockPos b)
    {
        this.level = level;
        final double length = Math.sqrt(a.distSqr(b));
        final int half = halfWidth(length);
        final int minX = (Math.min(a.getX(), b.getX()) - half) & ~15;
        final int minZ = (Math.min(a.getZ(), b.getZ()) - half) & ~15;
        final int maxX = (Math.max(a.getX(), b.getX()) + half) | 15;
        final int maxZ = (Math.max(a.getZ(), b.getZ()) + half) | 15;
        this.field = new HeightField(minX, minZ, maxX - minX + 1, maxZ - minZ + 1);
        Arrays.fill(field.flags, (byte) HeightField.UNKNOWN);
        for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++)
        {
            for (int cx = minX >> 4; cx <= maxX >> 4; cx++)
            {
                if (distanceToLine((cx << 4) + 8, (cz << 4) + 8, a, b) <= half + CHUNK_MARGIN)
                {
                    todo.add(new ChunkPos(cx, cz));
                }
            }
        }
        this.total = todo.size();
    }

    /**
     * Half the width of the surveyed band: a fixed room to manoeuvre plus a share of the distance.
     */
    public static int halfWidth(final double length)
    {
        return (int) Math.max(80, Math.min(256, 48 + 0.18 * length));
    }

    private static double distanceToLine(final int x, final int z, final BlockPos a, final BlockPos b)
    {
        final double dx = b.getX() - a.getX();
        final double dz = b.getZ() - a.getZ();
        final double lengthSq = dx * dx + dz * dz;
        final double t = lengthSq == 0 ? 0 : Math.max(0, Math.min(1, ((x - a.getX()) * dx + (z - a.getZ()) * dz) / lengthSq));
        return Math.hypot(x - (a.getX() + t * dx), z - (a.getZ() + t * dz));
    }

    /**
     * Advances the survey by one tick.
     *
     * @return true once every chunk of the band has been read
     */
    public boolean tick()
    {
        final long deadline = System.nanoTime() + TICK_BUDGET_NANOS;
        final ServerChunkCache chunks = level.getChunkSource();

        final Iterator<Reading> read = reading.iterator();
        while (read.hasNext())
        {
            final Reading one = read.next();
            if (!one.future().isDone())
            {
                continue;
            }
            final RouteProbe.Columns columns = one.future().join();
            if (columns == null)
            {
                unsaved.add(one.pos());
            }
            else
            {
                columns.copyTo(field, one.pos().x, one.pos().z);
                done++;
            }
            read.remove();
        }

        final Iterator<Loading> loaded = loading.iterator();
        while (loaded.hasNext() && System.nanoTime() < deadline)
        {
            final Loading one = loaded.next();
            if (!one.future().isDone())
            {
                continue;
            }
            final ChunkAccess chunk = one.future().isCompletedExceptionally() ? null : one.future().join().orElse(null);
            if (chunk != null)
            {
                RouteProbe.sample(level, chunk).copyTo(field, one.pos().x, one.pos().z);
            }
            chunks.removeRegionTicket(TICKET, one.pos(), 0, one.pos());
            loaded.remove();
            done++;
            generated++;
        }

        final HolderGetter<Block> blocks = level.holderLookup(Registries.BLOCK);
        final int minY = level.getMinBuildHeight();
        final int height = level.getHeight();
        while (!todo.isEmpty() && reading.size() < MAX_READING && System.nanoTime() < deadline)
        {
            final ChunkPos pos = todo.poll();
            final LevelChunk live = chunks.getChunkNow(pos.x, pos.z);
            if (live != null)
            {
                RouteProbe.sample(level, live).copyTo(field, pos.x, pos.z);
                done++;
                continue;
            }
            reading.add(new Reading(pos, chunks.chunkMap.read(pos)
              .thenApplyAsync(tag -> RouteProbe.read(tag.orElse(null), blocks, minY, height), Util.backgroundExecutor())
              .exceptionally(error -> null)));
        }

        while (!unsaved.isEmpty() && loading.size() < MAX_LOADING)
        {
            final ChunkPos pos = unsaved.poll();
            chunks.addRegionTicket(TICKET, pos, 0, pos);
            // Asked from another thread on purpose: called on the server thread, getChunkFuture waits for the chunk
            loading.add(new Loading(pos, CompletableFuture
              .supplyAsync(() -> chunks.getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true), Util.backgroundExecutor())
              .thenCompose(future -> future)));
        }
        return todo.isEmpty() && unsaved.isEmpty() && reading.isEmpty() && loading.isEmpty();
    }

    public float progress()
    {
        return total == 0 ? 1F : (float) done / total;
    }

    public int chunks()
    {
        return total;
    }

    /**
     * How many chunks had to be loaded or generated for real, the others being read from memory or from disk.
     */
    public int generated()
    {
        return generated;
    }

    public HeightField field()
    {
        return field;
    }

    /**
     * Drops the survey: lets go of every chunk still held.
     */
    public void cancel()
    {
        for (final Loading one : loading)
        {
            level.getChunkSource().removeRegionTicket(TICKET, one.pos(), 0, one.pos());
        }
        loading.clear();
        reading.clear();
        unsaved.clear();
        todo.clear();
    }
}
