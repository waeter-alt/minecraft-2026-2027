package fr.kodistan.interregne.routes;

import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.routes.plan.HeightField;
import fr.kodistan.interregne.routes.plan.Lead;
import fr.kodistan.interregne.routes.plan.RoutePlan;
import fr.kodistan.interregne.routes.plan.RoutePlanner;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Studies a route between two points without ever holding up the server: the country between them is surveyed a few
 * chunks per tick ({@link RouteSurvey}), then the line and its profile are worked out on a worker thread
 * ({@link RoutePlanner}), and the caller is called back on the server thread with the plan.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class RoutePlanning
{
    /** As the crow flies. Neighbouring colonies stand about a thousand blocks apart. */
    public static final int MAX_DISTANCE = 2600;
    private static final long PLAN_BUDGET_MILLIS = 6000;
    private static final float SURVEY_SHARE = 0.85F;

    private static final List<Study> STUDIES = new ArrayList<>();

    private static final class Study
    {
        private final BlockPos a;
        private final BlockPos b;
        private final ServerLevel level;
        @Nullable
        private final RouteEnds.End endA;
        @Nullable
        private final RouteEnds.End endB;
        private final Consumer<RoutePlan> done;
        private final ServerBossEvent bar;
        @Nullable
        private RouteSurvey survey;
        @Nullable
        private CompletableFuture<RoutePlan> planning;
        private int ticks;
        /** This study already stands in for one whose ends no route could reach. */
        private boolean fallback;

        private Study(final ServerLevel level, final BlockPos a, final BlockPos b, @Nullable final RouteEnds.End endA,
          @Nullable final RouteEnds.End endB, @Nullable final ServerPlayer watcher, final Consumer<RoutePlan> done)
        {
            this.a = a;
            this.b = b;
            this.level = level;
            this.endA = endA;
            this.endB = endB;
            this.done = done;
            this.survey = new RouteSurvey(level, a, b);
            this.bar = new ServerBossEvent(Component.literal("Étude de la route : arpentage du terrain"), BossEvent.BossBarColor.BLUE,
              BossEvent.BossBarOverlay.PROGRESS);
            this.bar.setProgress(0);
            if (watcher != null)
            {
                this.bar.addPlayer(watcher);
            }
        }
    }

    private RoutePlanning()
    {
    }

    /**
     * Starts studying the route from a to b, the two blocks of ground it starts and ends on (whatever stands on
     * them, a trading post for one, is ignored).
     *
     * @param watcher shown the progress, when not null
     * @param done    called on the server thread with the plan, or with null when no route could be found
     * @return false when the two ends are too far apart: nothing was started
     */
    public static boolean study(final ServerLevel level, final BlockPos a, final BlockPos b, @Nullable final ServerPlayer watcher,
      final Consumer<RoutePlan> done)
    {
        return study(level, a, b, null, null, watcher, done);
    }

    /**
     * Same, between two colony ends: the route keeps off the towns, their walls and every other colony's land, and
     * leaves straight out of the gates it is hooked to.
     */
    public static boolean study(final ServerLevel level, final RouteEnds.End endA, final RouteEnds.End endB, @Nullable final ServerPlayer watcher,
      final Consumer<RoutePlan> done)
    {
        return study(level, endA.at(), endB.at(), endA, endB, watcher, done);
    }

    private static boolean study(final ServerLevel level, final BlockPos a, final BlockPos b, @Nullable final RouteEnds.End endA,
      @Nullable final RouteEnds.End endB, @Nullable final ServerPlayer watcher, final Consumer<RoutePlan> done)
    {
        if (a.distSqr(b) > (double) MAX_DISTANCE * MAX_DISTANCE)
        {
            return false;
        }
        STUDIES.add(new Study(level, a, b, endA, endB, watcher, done));
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        final Iterator<Study> iterator = STUDIES.iterator();
        final List<Runnable> callbacks = new ArrayList<>();
        final List<Study> retries = new ArrayList<>();
        while (iterator.hasNext())
        {
            final Study study = iterator.next();
            study.ticks++;
            if (study.survey != null)
            {
                final boolean surveyed = study.survey.tick();
                study.bar.setProgress(SURVEY_SHARE * study.survey.progress());
                if (surveyed)
                {
                    final HeightField field = study.survey.field();
                    InterregneMod.LOGGER.info("Route surveyed in {} ticks: {} chunks, {} of them loaded or generated", study.ticks, study.survey.chunks(),
                      study.survey.generated());
                    study.survey = null;
                    final RouteRegistry registry = RouteRegistry.get(event.getServer());
                    registry.overlay(field);
                    // A road that already leads to B may be met square on instead of run alongside to B's gate
                    final List<int[][]> joinable = study.endB == null ? List.of() : registry.joinRoads(study.endA == null ? 0 : study.endA.colony(),
                      study.endB.colony());
                    final byte[] join = joinable.isEmpty() ? null : RoutePlanner.joinMask(field, joinable);
                    for (final BlockPos end : new BlockPos[] {study.a, study.b})
                    {
                        field.set(end.getX(), end.getZ(), end.getY(), end.getY(), 0);
                    }
                    if (study.endA != null && study.endB != null)
                    {
                        RouteEnds.exclude(study.level, field, study.endA, study.endB);
                    }
                    final Lead leadA = study.endA == null ? null : study.endA.lead();
                    final Lead leadB = study.endB == null ? null : study.endB.lead();
                    study.bar.setName(Component.literal("Étude de la route : calcul du tracé"));
                    final BlockPos a = study.a;
                    final BlockPos b = study.b;
                    study.planning = CompletableFuture.supplyAsync(
                      () -> RoutePlanner.plan(field, a.getX(), a.getZ(), b.getX(), b.getZ(), PLAN_BUDGET_MILLIS, leadA, leadB, join), Util.backgroundExecutor());
                }
            }
            else if (study.planning != null && study.planning.isDone())
            {
                RoutePlan plan = null;
                try
                {
                    plan = study.planning.join();
                }
                catch (final RuntimeException e)
                {
                    InterregneMod.LOGGER.error("Route planning failed", e);
                }
                InterregneMod.LOGGER.info("Route studied from {} to {} in {} ticks: {}", study.a.toShortString(), study.b.toShortString(), study.ticks,
                  plan == null ? "no route" : plan.length() + " cells, " + plan.count(RoutePlan.BRIDGE) + " bridges, " + plan.count(RoutePlan.TUNNEL)
                                                + " tunnels");
                final Study retry = plan == null ? fallback(study) : null;
                final List<ServerPlayer> watchers = new ArrayList<>(study.bar.getPlayers());
                study.bar.removeAllPlayers();
                iterator.remove();
                if (retry != null)
                {
                    // A town hall's door the road cannot get out of: the road stops at the edge of that colony's land
                    InterregneMod.LOGGER.info("Route from {} to {}: no way out of a town hall's door, trying from {} to {} (edge of the colony's land)",
                      study.a.toShortString(), study.b.toShortString(), retry.a.toShortString(), retry.b.toShortString());
                    watchers.forEach(retry.bar::addPlayer);
                    retries.add(retry);
                    continue;
                }
                final RoutePlan result = plan;
                callbacks.add(() -> study.done.accept(result));
            }
            else if (study.planning != null)
            {
                study.bar.setProgress(Math.min(0.99F, study.bar.getProgress() + 0.002F));
            }
        }
        STUDIES.addAll(retries);
        callbacks.forEach(Runnable::run);
    }

    /**
     * The same study with each end at a town hall's door moved to the edge of its colony's land, null when neither
     * end can be moved or this study is already such a fallback.
     */
    @Nullable
    private static Study fallback(final Study study)
    {
        if (study.fallback || study.endA == null || study.endB == null)
        {
            return null;
        }
        final RouteEnds.End movedA = RouteEnds.fallback(study.level, study.endA, study.endB.at());
        final RouteEnds.End movedB = RouteEnds.fallback(study.level, study.endB, study.endA.at());
        if (movedA == null && movedB == null)
        {
            return null;
        }
        final RouteEnds.End endA = movedA == null ? study.endA : movedA;
        final RouteEnds.End endB = movedB == null ? study.endB : movedB;
        if (endA.at().distSqr(endB.at()) > (double) MAX_DISTANCE * MAX_DISTANCE || endA.at().equals(endB.at()))
        {
            return null;
        }
        final Study retry = new Study(study.level, endA.at(), endB.at(), endA, endB, null, study.done);
        retry.fallback = true;
        retry.ticks = study.ticks;
        return retry;
    }

    @SubscribeEvent
    public static void onServerStopping(final ServerStoppingEvent event)
    {
        for (final Study study : STUDIES)
        {
            if (study.survey != null)
            {
                study.survey.cancel();
            }
            study.bar.removeAllPlayers();
        }
        STUDIES.clear();
    }
}
