package fr.kodistan.interregne.dev;

import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.colony.RouteSite;
import fr.kodistan.interregne.routes.RouteBuilder;
import fr.kodistan.interregne.routes.RoutePlanning;
import fr.kodistan.interregne.routes.RouteProbe;
import fr.kodistan.interregne.routes.RouteRegistry;
import fr.kodistan.interregne.routes.Routes;
import fr.kodistan.interregne.routes.RouteStep;
import fr.kodistan.interregne.routes.plan.RoutePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Dev-only entry point of the route capture tool (client.dev.RouteShot): studies a route like the game does, lays
 * the whole of it at once with no site and no show, and proposes the camera poses worth looking at — each bridge
 * from the side and from its deck, each tunnel from outside and inside, a view from above every so often.
 */
public final class RouteDev
{
    /** Poses proposed by the routes built in this run, relative to the routeshot directory. */
    public static final String AUTO_SHOTS = "shots_auto.txt";
    private static final int OVERVIEW_EVERY = 140;

    private static int built;

    private RouteDev()
    {
    }

    /**
     * Studies the route between the columns of a and b (their y is ignored: the ground is read) and sets every block
     * of it on the spot — or, with show, raises it through a real {@link RouteSite} in a few seconds, the way a
     * player sees it. Call on the server thread.
     *
     * @return completes with one line saying what was built, starting with ERROR when nothing was
     */
    public static CompletableFuture<String> build(final ServerLevel level, final BlockPos a, final BlockPos b, final boolean show)
    {
        final CompletableFuture<String> result = new CompletableFuture<>();
        final long started = System.currentTimeMillis();
        final BlockPos from = foot(level, a.getX(), a.getZ());
        final BlockPos to = foot(level, b.getX(), b.getZ());
        final boolean accepted = RoutePlanning.study(level, from, to, null, plan -> {
            try
            {
                if (plan == null)
                {
                    result.complete("ERROR no route from " + from.toShortString() + " to " + to.toShortString());
                    return;
                }
                final long studied = System.currentTimeMillis();
                final RouteBuilder builder = new RouteBuilder(level, plan);
                final int index = ++built;
                final List<String> poses = poses(level, plan, "r" + index);
                final Path dir = level.getServer().getServerDirectory().resolve("routeshot");
                Files.createDirectories(dir);
                Files.write(dir.resolve(AUTO_SHOTS), poses, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                final String head = "route r" + index + " " + from.toShortString() + " -> " + to.toShortString() + ": " + plan.length() + " cells, "
                                      + describe(plan) + "; study " + (studied - started) + " ms, ";
                if (show)
                {
                    RouteSite.launch(level, builder, 6, Component.literal("Route (test)"), () -> {
                        RouteRegistry.get(level.getServer()).record(plan, null, null);
                        RouteRegistry.get(level.getServer()).broadcast();
                        final String line = head + "site " + (System.currentTimeMillis() - studied) + " ms, " + poses.size() + " poses";
                        InterregneMod.LOGGER.info("RouteDev: {}", line);
                        result.complete(line);
                    });
                    return;
                }
                int blocks = 0;
                for (int i = 0; i < builder.cells(); i++)
                {
                    for (final RouteStep step : builder.slice(i))
                    {
                        BlockState state = step.state();
                        if (!state.isAir())
                        {
                            final BlockState shaped = Block.updateFromNeighbourShapes(state, level, step.pos());
                            state = shaped.isAir() ? state : shaped;
                        }
                        level.setBlock(step.pos(), state, Block.UPDATE_ALL);
                        blocks++;
                    }
                }
                RouteRegistry.get(level.getServer()).record(plan, null, null);
                RouteRegistry.get(level.getServer()).broadcast();
                final String line = head + blocks + " blocks laid in " + (System.currentTimeMillis() - studied) + " ms, " + poses.size() + " poses";
                InterregneMod.LOGGER.info("RouteDev: {}", line);
                result.complete(line);
            }
            catch (final IOException | RuntimeException e)
            {
                InterregneMod.LOGGER.error("RouteDev failed", e);
                result.complete("ERROR " + e);
            }
        });
        if (!accepted)
        {
            result.complete("ERROR too far: " + from.toShortString() + " to " + to.toShortString());
        }
        return result;
    }

    /** The block of ground a route ends on in this column: under the Poste commercial when one stands there. */
    private static BlockPos foot(final ServerLevel level, final int x, final int z)
    {
        final BlockPos top = new BlockPos(x, RouteProbe.of(level, x, z).ground(), z);
        return level.getBlockState(top).is(Routes.POSTE_COMMERCIAL.get()) ? top.below() : top;
    }

    /**
     * Sets a Poste commercial on the ground of a column, as a player would.
     */
    public static BlockPos post(final ServerLevel level, final int x, final int z)
    {
        final BlockPos pos = foot(level, x, z).above();
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos, Routes.POSTE_COMMERCIAL.get().defaultBlockState(), Block.UPDATE_ALL);
        RouteRegistry.get(level.getServer()).add(pos);
        return pos;
    }

    /**
     * Right-clicks the Poste commercial standing in a column, as that player.
     */
    public static String click(final ServerLevel level, final ServerPlayer player, final int x, final int z, final boolean sneak)
    {
        final BlockPos pos = foot(level, x, z).above();
        final BlockState state = level.getBlockState(pos);
        if (player == null || !state.is(Routes.POSTE_COMMERCIAL.get()))
        {
            return "ERROR no post at " + pos.toShortString();
        }
        player.setShiftKeyDown(sneak);
        state.useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        player.setShiftKeyDown(false);
        return "post at " + pos.toShortString();
    }

    private static String describe(final RoutePlan plan)
    {
        final StringBuilder text = new StringBuilder();
        int i = 0;
        while (i < plan.length())
        {
            int end = i;
            while (end + 1 < plan.length() && plan.mode[end + 1] == plan.mode[i])
            {
                end++;
            }
            if (plan.mode[i] != RoutePlan.SURFACE)
            {
                text.append(plan.mode[i] == RoutePlan.BRIDGE ? " B" : " T").append('[').append(i).append('+').append(end - i + 1).append(" y")
                  .append(plan.y[i]).append(']');
            }
            i = end + 1;
        }
        return text.isEmpty() ? "no structure" : text.toString().trim();
    }

    private static List<String> poses(final ServerLevel level, final RoutePlan plan, final String prefix)
    {
        final List<String> out = new ArrayList<>();
        final int n = plan.length();
        look(level, out, prefix + "_depart", plan.x[0] + 9, plan.y[0] + 7, plan.z[0] + 9, plan.x[Math.min(n - 1, 6)], plan.y[0], plan.z[Math.min(n - 1, 6)]);
        for (int i = OVERVIEW_EVERY / 2; i < n; i += OVERVIEW_EVERY)
        {
            final int back = Math.max(0, i - 45);
            final int ahead = Math.min(n - 1, i + 30);
            final int[] side = side(plan, back);
            look(level, out, prefix + "_vue" + i, plan.x[back] + side[0] * 16, plan.y[back] + 26, plan.z[back] + side[1] * 16, plan.x[ahead], plan.y[ahead],
              plan.z[ahead]);
            final int walk = Math.max(0, i - 8);
            out.add(pose(prefix + "_sol" + i, plan.x[walk] + 0.5, plan.y[walk] + 2.7, plan.z[walk] + 0.5, plan.x[ahead] + 0.5, plan.y[ahead] + 1.5,
              plan.z[ahead] + 0.5));
        }
        int i = 0;
        while (i < n)
        {
            int end = i;
            while (end + 1 < n && plan.mode[end + 1] == plan.mode[i])
            {
                end++;
            }
            final int mid = (i + end) / 2;
            final int length = end - i + 1;
            final int[] side = side(plan, mid);
            if (plan.mode[i] == RoutePlan.BRIDGE)
            {
                final int away = Math.max(16, (int) (length * 0.75));
                look(level, out, prefix + "_pont" + i + "_cote", plan.x[mid] + side[0] * away, plan.y[mid] + 5, plan.z[mid] + side[1] * away, plan.x[mid],
                  plan.y[mid] - 2, plan.z[mid]);
                look(level, out, prefix + "_pont" + i + "_bas", plan.x[mid] - side[0] * (away / 2 + 6), plan.y[mid] - 6, plan.z[mid] - side[1] * (away / 2 + 6),
                  plan.x[mid], plan.y[mid], plan.z[mid]);
                final int before = Math.max(0, i - 7);
                out.add(pose(prefix + "_pont" + i + "_tablier", plan.x[before] + 0.5, plan.y[before] + 2.7, plan.z[before] + 0.5, plan.x[end] + 0.5,
                  plan.y[end] + 1.5, plan.z[end] + 0.5));
            }
            else if (plan.mode[i] == RoutePlan.TUNNEL)
            {
                final int before = Math.max(0, i - 11);
                out.add(pose(prefix + "_tunnel" + i + "_entree", plan.x[before] + 0.5, plan.y[before] + 3.2, plan.z[before] + 0.5, plan.x[i] + 0.5,
                  plan.y[i] + 3, plan.z[i] + 0.5));
                final int inside = Math.min(end, i + Math.min(7, length / 2));
                out.add(pose(prefix + "_tunnel" + i + "_dedans", plan.x[inside] + 0.5, plan.y[inside] + 2.7, plan.z[inside] + 0.5, plan.x[end] + 0.5,
                  plan.y[end] + 2, plan.z[end] + 0.5));
                final int after = Math.min(n - 1, end + 11);
                out.add(pose(prefix + "_tunnel" + i + "_sortie", plan.x[after] + 0.5, plan.y[after] + 3.2, plan.z[after] + 0.5, plan.x[end] + 0.5,
                  plan.y[end] + 3, plan.z[end] + 0.5));
            }
            i = end + 1;
        }
        // The longest flight of steps, seen from its foot
        int best = -1;
        int bestRun = 2;
        int run = 0;
        for (int k = 1; k < n; k++)
        {
            run = plan.y[k] != plan.y[k - 1] && plan.mode[k] == RoutePlan.SURFACE ? run + 1 : Math.max(0, run - 1);
            if (run > bestRun)
            {
                bestRun = run;
                best = k;
            }
        }
        if (best >= 0)
        {
            final int foot = Math.max(0, best - bestRun - 9);
            out.add(pose(prefix + "_marches" + best, plan.x[foot] + 0.5, plan.y[foot] + 2.7, plan.z[foot] + 0.5, plan.x[best] + 0.5, plan.y[best] + 1.5,
              plan.z[best] + 0.5));
            final int[] side = side(plan, best);
            look(level, out, prefix + "_marches" + best + "_cote", plan.x[best] + side[0] * 14, plan.y[best] + 8, plan.z[best] + side[1] * 14,
              plan.x[Math.max(0, best - bestRun / 2)], plan.y[best], plan.z[Math.max(0, best - bestRun / 2)]);
        }
        return out;
    }

    /** Unit step across the route at a cell. */
    private static int[] side(final RoutePlan plan, final int i)
    {
        final int from = Math.max(1, Math.min(plan.length() - 1, i));
        final int dx = Integer.signum(plan.x[from] - plan.x[from - 1]);
        final int dz = Integer.signum(plan.z[from] - plan.z[from - 1]);
        return dx != 0 && dz != 0 ? new int[] {dx, -dz} : new int[] {-dz, dx};
    }

    /** A pose in the open: lifted clear of the ground when the wished height is under it. */
    private static void look(final ServerLevel level, final List<String> out, final String name, final int x, final int y, final int z, final int tx,
      final int ty, final int tz)
    {
        final int ground = RouteProbe.of(level, x, z).ground();
        out.add(pose(name, x + 0.5, Math.max(y, ground + 3), z + 0.5, tx + 0.5, ty + 0.5, tz + 0.5));
    }

    private static String pose(final String name, final double x, final double y, final double z, final double tx, final double ty, final double tz)
    {
        final double dx = tx - x;
        final double dy = ty - y;
        final double dz = tz - z;
        final double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        final double pitch = -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        return String.format(Locale.ROOT, "%s %.2f %.2f %.2f %.1f %.1f", name, x, y, z, yaw, pitch);
    }
}
