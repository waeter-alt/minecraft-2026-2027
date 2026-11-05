package fr.kodistan.interregne.dev;

import com.minecolonies.api.colony.IColony;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.production.Huts;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * Dev-only gameplay checks, so production and economy can be verified without anyone playing.
 * <p>
 * Drop {@code scenario/request.properties} in the game directory, then open a world holding a colony (a copy: the
 * scenario changes it):
 * <pre>
 * scenario=restaurant   (see SCENARIOS)
 * ticks=24000           (optional: how long the game runs, 24000 = one Minecraft day)
 * every=2400            (optional: ticks between two lines of measures)
 * </pre>
 * The game is sprinted (run as fast as the machine allows), then {@code scenario/done.txt} holds the setup, the
 * measures over time and the PASS / FAIL lines.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class ScenarioRunner
{
    private static final Map<String, Supplier<Scenario>> SCENARIOS = Map.of(
      "restaurant", () -> new RestaurantScenario(false),
      "restaurant-healthy", () -> new RestaurantScenario(true),
      "intendance", IntendanceScenario::new,
      "apiary", ApiaryScenario::new,
      "production", ProductionScenario::new);

    /** Colonies load their buildings a little after the world: wait this long before touching them. */
    private static final int WARMUP_TICKS = 200;

    private enum Phase
    {
        IDLE, WARMUP, RUN
    }

    private static Phase phase = Phase.IDLE;
    private static long ticks;
    private static String name;
    private static Scenario scenario;
    private static IColony colony;
    private static int duration;
    private static int every;
    private static long started;
    private static final List<String> report = new ArrayList<>();

    private ScenarioRunner()
    {
    }

    private static Path dir(final MinecraftServer server)
    {
        return server.getServerDirectory().resolve("scenario");
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
            switch (phase)
            {
                case IDLE ->
                {
                    if (ticks % 20 == 0 && Files.exists(dir(server).resolve("request.properties")))
                    {
                        start(server);
                    }
                }
                case WARMUP -> warmup(server);
                case RUN -> run(server);
            }
        }
        catch (final Exception e)
        {
            InterregneMod.LOGGER.error("Scenario failed", e);
            report.add("ERROR " + e);
            finish(server);
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
        report.clear();
        name = props.getProperty("scenario", "restaurant").trim();
        duration = Integer.parseInt(props.getProperty("ticks", "24000").trim());
        every = Integer.parseInt(props.getProperty("every", "2400").trim());
        final Supplier<Scenario> factory = SCENARIOS.get(name);
        if (factory == null)
        {
            report.add("ERROR unknown scenario " + name + ", known: " + SCENARIOS.keySet());
            finish(server);
            return;
        }
        scenario = factory.get();
        ticks = 0;
        phase = Phase.WARMUP;
    }

    private static void warmup(final MinecraftServer server)
    {
        if (ticks < WARMUP_TICKS)
        {
            return;
        }
        colony = null;
        for (final IColony candidate : Huts.colonies())
        {
            colony = candidate;
            break;
        }
        if (colony == null || !(colony.getWorld() instanceof ServerLevel level))
        {
            report.add("ERROR no loaded colony in this world");
            finish(server);
            return;
        }
        report.add("Scenario " + name + " on colony " + colony.getName() + ", " + duration + " ticks (" + duration / 24000.0 + " day)");
        report.add("-- setup");
        report.addAll(scenario.setup(colony, level));
        report.add("-- measures (tick: ...)");
        report.add("0: " + scenario.snapshot(colony, level));
        server.tickRateManager().requestGameToSprint(duration + 20);
        started = System.currentTimeMillis();
        ticks = 0;
        phase = Phase.RUN;
    }

    private static void run(final MinecraftServer server)
    {
        final ServerLevel level = (ServerLevel) colony.getWorld();
        if (ticks % every == 0 || ticks >= duration)
        {
            report.add(ticks + ": " + scenario.snapshot(colony, level));
        }
        if (ticks < duration)
        {
            return;
        }
        server.tickRateManager().stopSprinting();
        report.add("-- verdict (ran in " + (System.currentTimeMillis() - started) / 1000 + " s real time)");
        report.addAll(scenario.verdict(colony, level));
        finish(server);
    }

    private static void finish(final MinecraftServer server)
    {
        phase = Phase.IDLE;
        if (server.tickRateManager().isSprinting())
        {
            server.tickRateManager().stopSprinting();
        }
        try
        {
            Files.createDirectories(dir(server));
            Files.write(dir(server).resolve("done.txt"), report, StandardCharsets.UTF_8);
        }
        catch (final IOException e)
        {
            InterregneMod.LOGGER.error("Scenario could not write its report", e);
        }
        InterregneMod.LOGGER.info("Scenario done: {}", report.isEmpty() ? "" : report.getLast());
    }
}
