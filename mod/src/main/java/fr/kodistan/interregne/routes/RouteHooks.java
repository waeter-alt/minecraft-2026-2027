package fr.kodistan.interregne.routes;

import com.minecolonies.api.colony.IColony;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.colony.RouteSite;
import fr.kodistan.interregne.walls.WallLayout;
import fr.kodistan.interregne.walls.WallRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * Keeps the roads hooked to a colony's gates when its wall changes (rebuilt larger, moved, or put up where the road
 * used to stop at the edge of its land).
 */
public final class RouteHooks
{
    /** A road passing this close to a gate goes through it. */
    private static final int THROUGH = 3;
    /** A new end this close to the old one needs no connector. */
    private static final int SAME = 2;

    private RouteHooks()
    {
    }

    /**
     * Once a wall is built: a road the new trace crosses goes through the gate set there, and its stretch inside the
     * wall stays as a street. A road whose end no longer meets a gate gets a short connector to the nearest one.
     */
    public static void onWallBuilt(final ServerLevel level, final IColony colony, final WallLayout layout)
    {
        final RouteRegistry registry = RouteRegistry.get(level.getServer());
        final WallRegistry.Wall wall = WallRegistry.get(level.getServer()).of(colony);
        if (wall == null || layout.gates().isEmpty())
        {
            return;
        }
        boolean changed = false;
        for (final RouteRegistry.Hook hook : registry.hooks(colony.getID()))
        {
            final int[][] cells = registry.cells(hook.road());
            final WallLayout.Gate through = through(layout, cells);
            if (through != null)
            {
                registry.rehook(hook, colony.getID(), through.index());
                changed = true;
                continue;
            }
            final int far = hook.first() ? cells[0].length - 1 : 0;
            final BlockPos toward = new BlockPos(cells[0][far], hook.at().getY(), cells[1][far]);
            final RouteEnds.End target = RouteEnds.of(level, hook.at(), toward);
            if (Math.abs(target.at().getX() - hook.at().getX()) <= SAME && Math.abs(target.at().getZ() - hook.at().getZ()) <= SAME)
            {
                registry.rehook(hook, colony.getID(), target.gate());
                changed = true;
                continue;
            }
            if (wall.closed() && wall.points().size() > 2 && RouteEnds.inside(wall.points(), hook.at().getX(), hook.at().getZ()))
            {
                // The road stops inside the new ring without crossing it: nothing to hook it to from the outside
                InterregneMod.LOGGER.warn("Route {} : son bout est dans la nouvelle muraille de la colonie {} sans la traverser", hook.road(), colony.getID());
                continue;
            }
            connect(level, registry, hook, target);
            changed = true;
        }
        if (changed)
        {
            registry.broadcast();
        }
    }

    private static WallLayout.Gate through(final WallLayout layout, final int[][] cells)
    {
        for (final WallLayout.Gate gate : layout.gates())
        {
            for (int i = 0; i < cells[0].length; i++)
            {
                if (Math.abs(cells[0][i] - gate.x()) <= THROUGH && Math.abs(cells[1][i] - gate.z()) <= THROUGH)
                {
                    return gate;
                }
            }
        }
        return null;
    }

    /**
     * Unhooks the old end and builds a short road from it to the gate: the new road carries the hook once done.
     */
    private static void connect(final ServerLevel level, final RouteRegistry registry, final RouteRegistry.Hook hook, final RouteEnds.End target)
    {
        registry.rehook(hook, 0, -1);
        final RouteEnds.End from = new RouteEnds.End(hook.at(), null, 0, -1, false);
        final boolean started = RoutePlanning.study(level, from, target, null, plan -> {
            if (plan == null)
            {
                InterregneMod.LOGGER.warn("Route {} : aucun raccord possible jusqu'à la porte {} de la colonie {}", hook.road(), target.gate(), target.colony());
                return;
            }
            final int seconds = Math.max(3, Math.min(10, 3 + plan.length() / 100));
            RouteSite.launch(level, new RouteBuilder(level, plan), seconds, Component.literal("⚒ Raccord de route"), () -> {
                final RouteRegistry now = RouteRegistry.get(level.getServer());
                now.record(plan, null, target);
                now.broadcast();
            });
        });
        if (!started)
        {
            InterregneMod.LOGGER.warn("Route {} : porte trop loin pour un raccord", hook.road());
        }
    }
}
