package fr.kodistan.interregne.dev;

import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.routes.RouteProbe;
import fr.kodistan.interregne.walls.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Dev-only: writes the relief of a square of the overworld to a file, so the route planner can be run and judged
 * outside the game on real terrain.
 * <p>
 * Drop {@code routedev/request.properties} in the game directory:
 * <pre>
 * dump=name:x0,z0,x1,z1   (block bounds, inclusive)
 * around=minecraft:jagged_peaks   (optional: the bounds are then relative to the nearest place of that biome,
 *                                  found from the origin; done.txt says where it is)
 * stop=true               (optional: stop the server once the file is written)
 * </pre>
 * Chunks are loaded (generated when new) through tickets, a few hundred at a time, and released as soon as they are
 * probed. The result is {@code routedev/<name>.rter}, then {@code routedev/done.txt}.
 * <p>
 * File, big-endian: int magic "RTER", int version 1, int x0, int z0, int w, int h, then w*h shorts of ground,
 * w*h shorts of bed, w*h bytes of flags, all indexed (z - z0) * w + (x - x0).
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class TerrainDump
{
    public static final int MAGIC = 0x52544552;
    public static final int WATER = 1;
    public static final int BLOCKED = 2;
    public static final int LAVA = 4;
    public static final int ICE = 8;

    /** The ticket's value is the tile, not the chunk: two tiles share their margin chunks and release them apart. */
    private static final TicketType<ChunkPos> TICKET = TicketType.create("interregne_terrain_dump", Comparator.comparingLong(ChunkPos::toLong));
    private static final int TILE = 16;
    private static final int MAX_ACTIVE = 2;
    private static final long BUDGET_NANOS = 40_000_000L;

    /**
     * A square of chunks to probe; its tickets also cover one chunk of margin around it, so reading a tree across a
     * chunk border never loads a chunk on the spot.
     */
    private static final class Tile
    {
        final int cx0;
        final int cz0;
        final int cx1;
        final int cz1;
        boolean loaded;
        int waiting;
        int cursor;

        Tile(final int cx0, final int cz0, final int cx1, final int cz1)
        {
            this.cx0 = cx0;
            this.cz0 = cz0;
            this.cx1 = cx1;
            this.cz1 = cz1;
        }

        ChunkPos key()
        {
            return new ChunkPos(cx0, cz0);
        }

        int width()
        {
            return cx1 - cx0 + 1;
        }

        int count()
        {
            return width() * (cz1 - cz0 + 1);
        }
    }

    private static boolean running;
    private static long ticks;
    private static String name;
    private static boolean stop;
    private static String located;
    private static int x0;
    private static int z0;
    private static int w;
    private static int h;
    private static short[] ground;
    private static short[] bed;
    private static byte[] flags;
    private static final List<Tile> tiles = new ArrayList<>();
    private static int nextTile;
    private static final Deque<Tile> active = new ArrayDeque<>();
    private static int chunksDone;
    private static int chunksTotal;
    private static long started;
    private static long lastLog;
    /** What the blocked columns are made of, to tell a village from a natural block missing in Terrain.natural(). */
    private static final Map<String, Integer> blockedKinds = new HashMap<>();

    private TerrainDump()
    {
    }

    private static Path dir(final MinecraftServer server)
    {
        return server.getServerDirectory().resolve("routedev");
    }

    @SubscribeEvent
    public static void onTick(final ServerTickEvent.Post event)
    {
        if (FMLEnvironment.production)
        {
            return;
        }
        final MinecraftServer server = event.getServer();
        ticks++;
        try
        {
            if (!running)
            {
                if (ticks % 20 == 0 && Files.exists(dir(server).resolve("request.properties")))
                {
                    start(server);
                }
                return;
            }
            work(server);
        }
        catch (final Exception e)
        {
            InterregneMod.LOGGER.error("Terrain dump failed", e);
            release(server.overworld());
            finish(server, List.of("ERROR " + e));
        }
    }

    private static void start(final MinecraftServer server) throws IOException
    {
        final Path request = dir(server).resolve("request.properties");
        final Properties props = new Properties();
        try (var reader = Files.newBufferedReader(request, StandardCharsets.UTF_8))
        {
            props.load(reader);
        }
        Files.delete(request);
        Files.deleteIfExists(dir(server).resolve("done.txt"));
        stop = Boolean.parseBoolean(props.getProperty("stop", "false").trim());
        final String spec = props.getProperty("dump", "").trim();
        final String[] parts = spec.split(":");
        final String[] bounds = parts.length == 2 ? parts[1].split(",") : new String[0];
        if (bounds.length != 4)
        {
            finish(server, List.of("ERROR expected dump=name:x0,z0,x1,z1, got: " + spec));
            return;
        }
        name = parts[0].trim();
        int centreX = 0;
        int centreZ = 0;
        final String around = props.getProperty("around", "").trim();
        if (!around.isEmpty())
        {
            final ResourceLocation biome = ResourceLocation.parse(around);
            final var found = server.overworld().findClosestBiome3d(holder -> holder.is(biome), new BlockPos(0, 100, 0), 12000, 64, 64);
            if (found == null)
            {
                finish(server, List.of("ERROR no " + around + " within 12000 blocks"));
                return;
            }
            centreX = found.getFirst().getX();
            centreZ = found.getFirst().getZ();
            located = around + " at " + centreX + ", " + centreZ;
            InterregneMod.LOGGER.info("Terrain dump: {}", located);
        }
        else
        {
            located = null;
        }
        final int ax = centreX + Integer.parseInt(bounds[0].trim());
        final int az = centreZ + Integer.parseInt(bounds[1].trim());
        final int bx = centreX + Integer.parseInt(bounds[2].trim());
        final int bz = centreZ + Integer.parseInt(bounds[3].trim());
        x0 = Math.min(ax, bx);
        z0 = Math.min(az, bz);
        w = Math.abs(bx - ax) + 1;
        h = Math.abs(bz - az) + 1;
        ground = new short[w * h];
        bed = new short[w * h];
        flags = new byte[w * h];

        tiles.clear();
        active.clear();
        blockedKinds.clear();
        nextTile = 0;
        chunksDone = 0;
        chunksTotal = 0;
        final int cxMax = (x0 + w - 1) >> 4;
        final int czMax = (z0 + h - 1) >> 4;
        for (int cz = z0 >> 4; cz <= czMax; cz += TILE)
        {
            for (int cx = x0 >> 4; cx <= cxMax; cx += TILE)
            {
                final Tile tile = new Tile(cx, cz, Math.min(cx + TILE - 1, cxMax), Math.min(cz + TILE - 1, czMax));
                tiles.add(tile);
                chunksTotal += tile.count();
            }
        }
        started = System.currentTimeMillis();
        lastLog = started;
        running = true;
        InterregneMod.LOGGER.info("Terrain dump {}: {}x{} blocks from ({}, {}), {} chunks", name, w, h, x0, z0, chunksTotal);
    }

    private static void work(final MinecraftServer server) throws IOException
    {
        final ServerLevel level = server.overworld();
        final ServerChunkCache chunks = level.getChunkSource();
        while (active.size() < MAX_ACTIVE && nextTile < tiles.size())
        {
            final Tile tile = tiles.get(nextTile++);
            ticket(chunks, tile, true);
            active.add(tile);
        }

        final long deadline = System.nanoTime() + BUDGET_NANOS;
        while (!active.isEmpty() && System.nanoTime() < deadline)
        {
            final Tile tile = active.peekFirst();
            if (!tile.loaded && !loaded(chunks, tile))
            {
                break;
            }
            tile.loaded = true;
            while (tile.cursor < tile.count() && System.nanoTime() < deadline)
            {
                final int cx = tile.cx0 + tile.cursor % tile.width();
                final int cz = tile.cz0 + tile.cursor / tile.width();
                final LevelChunk chunk = chunks.getChunkNow(cx, cz);
                if (chunk == null)
                {
                    // Dropped between two ticks although ticketed: wait for it to come back
                    tile.loaded = false;
                    tile.waiting = 0;
                    break;
                }
                probe(level, chunk);
                tile.cursor++;
                chunksDone++;
            }
            if (tile.cursor < tile.count())
            {
                break;
            }
            ticket(chunks, tile, false);
            active.removeFirst();
        }

        final long now = System.currentTimeMillis();
        if (now - lastLog >= 5000)
        {
            lastLog = now;
            InterregneMod.LOGGER.info("Terrain dump {}: {}/{} chunks, {} chunks/s, {} loaded", name, chunksDone, chunksTotal,
              String.format("%.0f", chunksDone * 1000.0 / Math.max(1, now - started)), chunks.getLoadedChunksCount());
        }
        if (active.isEmpty() && nextTile >= tiles.size())
        {
            write(server);
        }
    }

    private static void ticket(final ServerChunkCache chunks, final Tile tile, final boolean add)
    {
        final ChunkPos key = tile.key();
        for (int cz = tile.cz0 - 1; cz <= tile.cz1 + 1; cz++)
        {
            for (int cx = tile.cx0 - 1; cx <= tile.cx1 + 1; cx++)
            {
                if (add)
                {
                    chunks.addRegionTicket(TICKET, new ChunkPos(cx, cz), 0, key);
                }
                else
                {
                    chunks.removeRegionTicket(TICKET, new ChunkPos(cx, cz), 0, key);
                }
            }
        }
    }

    /**
     * True once every chunk of the tile and its margin is there; resumes from the first one that was missing.
     */
    private static boolean loaded(final ServerChunkCache chunks, final Tile tile)
    {
        final int width = tile.width() + 2;
        final int total = width * (tile.cz1 - tile.cz0 + 3);
        while (tile.waiting < total)
        {
            if (chunks.getChunkNow(tile.cx0 - 1 + tile.waiting % width, tile.cz0 - 1 + tile.waiting / width) == null)
            {
                return false;
            }
            tile.waiting++;
        }
        return true;
    }

    private static void probe(final ServerLevel level, final LevelChunk chunk)
    {
        final int minY = level.getMinBuildHeight();
        final int baseX = chunk.getPos().getMinBlockX();
        final int baseZ = chunk.getPos().getMinBlockZ();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lz = 0; lz < 16; lz++)
        {
            final int z = baseZ + lz;
            if (z < z0 || z >= z0 + h)
            {
                continue;
            }
            for (int lx = 0; lx < 16; lx++)
            {
                final int x = baseX + lx;
                if (x < x0 || x >= x0 + w)
                {
                    continue;
                }
                column(level, chunk, pos, x, z, minY, (z - z0) * w + (x - x0));
            }
        }
    }

    /**
     * One column: the highest block that is neither vegetation nor air is the ground. On a liquid the ground is its
     * surface and the bed lies under it; on ice likewise, the bed under the ice and the water it covers.
     */
    private static void column(final ServerLevel level, final LevelChunk chunk, final BlockPos.MutableBlockPos pos, final int x, final int z, final int minY,
      final int index)
    {
        int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15);
        pos.set(x, y, z);
        BlockState state = chunk.getBlockState(pos);
        while (y > minY && state.getFluidState().isEmpty() && Terrain.clearable(level, pos, state))
        {
            y--;
            pos.setY(y);
            state = chunk.getBlockState(pos);
        }
        int flag = 0;
        int under = y;
        final FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty())
        {
            flag = fluid.is(FluidTags.LAVA) ? LAVA : WATER;
            under = y - 1;
            while (under > minY)
            {
                pos.setY(under);
                final BlockState below = chunk.getBlockState(pos);
                if (below.getFluidState().isEmpty() && !Terrain.clearable(level, pos, below))
                {
                    break;
                }
                under--;
            }
        }
        else if (RouteProbe.sheetIce(state))
        {
            flag = ICE;
            under = y - 1;
            while (under > minY)
            {
                pos.setY(under);
                final BlockState below = chunk.getBlockState(pos);
                if (!below.is(BlockTags.ICE) && below.getFluidState().isEmpty() && !Terrain.clearable(level, pos, below))
                {
                    break;
                }
                under--;
            }
        }
        else if (!Terrain.natural(state))
        {
            flag = BLOCKED;
            blockedKinds.merge(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), 1, Integer::sum);
        }
        ground[index] = (short) y;
        bed[index] = (short) under;
        flags[index] = (byte) flag;
    }

    private static void write(final MinecraftServer server) throws IOException
    {
        Files.createDirectories(dir(server));
        final Path file = dir(server).resolve(name + ".rter");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file), 1 << 16)))
        {
            out.writeInt(MAGIC);
            out.writeInt(1);
            out.writeInt(x0);
            out.writeInt(z0);
            out.writeInt(w);
            out.writeInt(h);
            for (final short value : ground)
            {
                out.writeShort(value);
            }
            for (final short value : bed)
            {
                out.writeShort(value);
            }
            out.write(flags);
        }
        final long millis = System.currentTimeMillis() - started;
        final List<String> report = new ArrayList<>();
        report.add("Terrain dump " + name + ": " + w + "x" + h + " blocks from (" + x0 + ", " + z0 + ")");
        report.add("file " + file.getFileName() + ", " + Files.size(file) + " bytes");
        blockedKinds.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(25)
          .forEach(kind -> report.add("blocked " + kind.getValue() + " x " + kind.getKey()));
        report.add(chunksTotal + " chunks in " + millis / 1000 + " s, " + String.format("%.0f", chunksTotal * 1000.0 / Math.max(1, millis)) + " chunks/s");
        finish(server, report);
    }

    private static void release(final ServerLevel level)
    {
        for (final Tile tile : active)
        {
            ticket(level.getChunkSource(), tile, false);
        }
        active.clear();
    }

    private static void finish(final MinecraftServer server, final List<String> report)
    {
        running = false;
        ground = null;
        bed = null;
        flags = null;
        tiles.clear();
        try
        {
            Files.createDirectories(dir(server));
            final List<String> lines = new ArrayList<>(report);
            if (located != null)
            {
                lines.add(0, "located " + located);
            }
            Files.write(dir(server).resolve("done.txt"), lines, StandardCharsets.UTF_8);
        }
        catch (final IOException e)
        {
            InterregneMod.LOGGER.error("Terrain dump could not write its report", e);
        }
        InterregneMod.LOGGER.info("Terrain dump done: {}", report.getLast());
        if (stop)
        {
            server.halt(false);
        }
    }
}
