package fr.kodistan.interregne.client.dev;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.dev.RouteDev;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Dev-only world capture, so a route can be looked at in the real game without anyone at the keyboard.
 * <p>
 * Drop {@code routeshot/request.properties} in the client's game directory:
 * <pre>
 * world=Routes_A                 (optional: opened from the title screen when no world is loaded)
 * shots=routeshot/shots.txt      (optional, relative to the game directory: one pose per line,
 *                                 "name x y z yaw pitch" — the camera's own position, Minecraft degrees;
 *                                 empty lines and lines starting with # are skipped)
 * posts=x,z;x,z;...             (optional: a Poste commercial set on the ground of each column first)
 * routes=ax,ay,az,bx,by,bz;...   (optional: routes laid at once through {@link RouteDev#build} next; the poses
 *                                 each one proposes for itself are captured after those of the pose file)
 * clicks=x,z,sneak;...           (optional: after the routes, the player right-clicks the Poste commercial of each
 *                                 column in turn — sneaking when sneak is true — as someone playing would)
 * clickwait=300                  (optional: ticks left to each click to do its work before the next)
 * show=true                      (optional: the routes rise through a real site instead of being laid at once)
 * home=x,y,z,yaw,pitch           (optional: where the player is left at the end, in creative — with quit=true
 *                                 the world is saved that way, ready to be opened and played)
 * size=1280x720                  (optional: window size)
 * settle=60                      (optional: ticks to wait at least after each teleport)
 * quit=true                      (optional: close the game once done)
 * </pre>
 * The player is put in spectator at noon under a clear sky, the interface hidden; each pose is captured into
 * {@code routeshot/out/<name>.png} once the chunks around it have stopped arriving, then {@code routeshot/done.txt}
 * lists the routes, the files and any error.
 */
@EventBusSubscriber(modid = InterregneMod.MODID, value = Dist.CLIENT)
public final class RouteShot
{
    private static final int LOAD_TIMEOUT_TICKS = 20 * 180;
    private static final int STABLE_TICKS = 20;
    private static final int SHOT_TIMEOUT_TICKS = 400;
    private static final int RENDER_DISTANCE = 16;

    private enum Phase
    {
        IDLE, LOADING, SETUP, ROUTES, MOVE, WAIT, DONE
    }

    private record Pose(String name, double x, double y, double z, float yaw, float pitch)
    {
    }

    private static Phase phase = Phase.IDLE;
    private static int ticks;
    private static String world;
    private static boolean worldAsked;
    private static int width;
    private static int height;
    private static int settle;
    private static boolean quit;
    private static final List<Pose> poses = new ArrayList<>();
    private static final List<BlockPos[]> routes = new ArrayList<>();
    private static final List<int[]> posts = new ArrayList<>();
    private static double[] home;
    private static boolean show;
    private static final List<int[]> clicks = new ArrayList<>();
    private static int clickWait;
    private static int clickIndex;
    private static int clickTimer;
    private static int index;
    private static CompletableFuture<?> pending;
    private static int stable;
    private static int lastSections;
    private static int lastChunks;
    private static final List<String> report = new ArrayList<>();
    private static long started;

    private static boolean optionsSaved;
    private static int savedWidth;
    private static int savedHeight;
    private static boolean savedHideGui;
    private static boolean savedPause;
    private static CloudStatus savedClouds;
    private static int savedDistance;

    private RouteShot()
    {
    }

    private static Path dir()
    {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("routeshot");
    }

    @SubscribeEvent
    public static void onTick(final ClientTickEvent.Post event)
    {
        if (FMLEnvironment.production)
        {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        ticks++;
        try
        {
            switch (phase)
            {
                case IDLE ->
                {
                    if (ticks % 20 == 0 && Files.exists(dir().resolve("request.properties")))
                    {
                        start(mc);
                    }
                }
                case LOADING -> load(mc);
                case SETUP -> setup(mc);
                case ROUTES -> routes(mc);
                case MOVE -> move(mc);
                case WAIT -> capture(mc);
                case DONE -> finish(mc);
            }
        }
        catch (final Exception e)
        {
            report.add("ERROR in " + phase + (phase == Phase.MOVE || phase == Phase.WAIT ? " at " + poses.get(index).name() : "") + ": " + e);
            InterregneMod.LOGGER.error("RouteShot failed", e);
            phase = Phase.DONE;
        }
    }

    private static void start(final Minecraft mc) throws IOException
    {
        final Path request = dir().resolve("request.properties");
        final Properties props = new Properties();
        try (var reader = Files.newBufferedReader(request, StandardCharsets.UTF_8))
        {
            props.load(reader);
        }
        Files.delete(request);
        Files.deleteIfExists(dir().resolve("done.txt"));
        final Path out = dir().resolve("out");
        if (Files.exists(out))
        {
            try (Stream<Path> files = Files.list(out))
            {
                for (final Path file : files.toList())
                {
                    Files.delete(file);
                }
            }
        }
        Files.createDirectories(out);

        report.clear();
        poses.clear();
        routes.clear();
        index = 0;
        pending = null;
        worldAsked = false;
        optionsSaved = false;
        started = System.currentTimeMillis();
        world = props.getProperty("world", "").trim();
        final String[] size = props.getProperty("size", "1280x720").trim().split("x");
        width = Integer.parseInt(size[0].trim());
        height = Integer.parseInt(size[1].trim());
        settle = Integer.parseInt(props.getProperty("settle", "60").trim());
        quit = Boolean.parseBoolean(props.getProperty("quit", "true").trim());

        posts.clear();
        for (final String spec : props.getProperty("posts", "").split(";"))
        {
            if (!spec.isBlank())
            {
                final String[] n = spec.trim().split(",");
                posts.add(new int[] {Integer.parseInt(n[0].trim()), Integer.parseInt(n[1].trim())});
            }
        }
        show = Boolean.parseBoolean(props.getProperty("show", "false").trim());
        clicks.clear();
        clickIndex = 0;
        clickTimer = 0;
        clickWait = Integer.parseInt(props.getProperty("clickwait", "300").trim());
        for (final String spec : props.getProperty("clicks", "").split(";"))
        {
            if (!spec.isBlank())
            {
                final String[] n = spec.trim().split(",");
                clicks.add(new int[] {Integer.parseInt(n[0].trim()), Integer.parseInt(n[1].trim()), n.length > 2 && Boolean.parseBoolean(n[2].trim()) ? 1 : 0});
            }
        }
        home = null;
        if (!props.getProperty("home", "").isBlank())
        {
            final String[] n = props.getProperty("home").trim().split(",");
            home = new double[5];
            for (int i = 0; i < 5; i++)
            {
                home[i] = Double.parseDouble(n[i].trim());
            }
        }
        for (final String spec : props.getProperty("routes", "").split(";"))
        {
            if (spec.isBlank())
            {
                continue;
            }
            final String[] n = spec.trim().split(",");
            if (n.length != 6)
            {
                report.add("ERROR route ignored, expected ax,ay,az,bx,by,bz: " + spec.trim());
                continue;
            }
            routes.add(new BlockPos[] {
              new BlockPos(Integer.parseInt(n[0].trim()), Integer.parseInt(n[1].trim()), Integer.parseInt(n[2].trim())),
              new BlockPos(Integer.parseInt(n[3].trim()), Integer.parseInt(n[4].trim()), Integer.parseInt(n[5].trim()))});
        }

        Files.deleteIfExists(dir().resolve(RouteDev.AUTO_SHOTS));
        final Path shots = mc.gameDirectory.toPath().resolve(props.getProperty("shots", "routeshot/shots.txt").trim());
        if (Files.exists(shots))
        {
            readPoses(shots);
        }
        else if (routes.isEmpty())
        {
            report.add("ERROR no pose file " + shots);
        }
        InterregneMod.LOGGER.info("RouteShot: {} routes, {} poses, world '{}'", routes.size(), poses.size(), world);
        ticks = 0;
        phase = Phase.LOADING;
    }

    private static void load(final Minecraft mc)
    {
        if (ticks > LOAD_TIMEOUT_TICKS)
        {
            report.add("ERROR timed out opening the world, screen: " + (mc.screen == null ? "none" : mc.screen.getClass().getName()));
            phase = Phase.DONE;
            return;
        }
        if (mc.level == null || mc.player == null)
        {
            if (!world.isEmpty() && !worldAsked && mc.getOverlay() == null && mc.screen instanceof TitleScreen)
            {
                worldAsked = true;
                mc.createWorldOpenFlows().openWorld(world, () -> mc.setScreen(new TitleScreen()));
            }
            return;
        }
        final IntegratedServer server = mc.getSingleplayerServer();
        if (server == null)
        {
            report.add("ERROR not a singleplayer world");
            phase = Phase.DONE;
            return;
        }
        if (server.getPlayerList().getPlayer(mc.player.getUUID()) == null)
        {
            return;
        }

        final Window window = mc.getWindow();
        savedWidth = window.getWidth();
        savedHeight = window.getHeight();
        savedHideGui = mc.options.hideGui;
        savedPause = mc.options.pauseOnLostFocus;
        savedClouds = mc.options.cloudStatus().get();
        savedDistance = mc.options.renderDistance().get();
        optionsSaved = true;
        resize(mc);
        mc.options.hideGui = true;
        // A window in the background would pause the game, and the integrated server with it
        mc.options.pauseOnLostFocus = false;
        mc.options.cloudStatus().set(CloudStatus.OFF);
        mc.options.renderDistance().set(RENDER_DISTANCE);

        final UUID id = mc.player.getUUID();
        pending = server.submit(() -> {
            final ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null)
            {
                player.setGameMode(GameType.SPECTATOR);
            }
            server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            server.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
            server.overworld().setDayTime(6000);
            server.overworld().setWeatherParameters(1_000_000, 0, false, false);
            for (final int[] post : posts)
            {
                report.add("post " + RouteDev.post(server.overworld(), post[0], post[1]).toShortString());
            }
        });
        ticks = 0;
        phase = Phase.SETUP;
    }

    private static void resize(final Minecraft mc)
    {
        final Window window = mc.getWindow();
        if (window.getWidth() != width || window.getHeight() != height)
        {
            final long handle = window.getWindow();
            final int[] w = new int[1];
            final int[] h = new int[1];
            GLFW.glfwRestoreWindow(handle);
            GLFW.glfwSetWindowSize(handle, width, height);
            GLFW.glfwGetWindowSize(handle, w, h);
            if (w[0] != width || h[0] != height)
            {
                window.setWindowed(width, height);
                GLFW.glfwGetWindowSize(handle, w, h);
            }
            mc.resizeDisplay();
            InterregneMod.LOGGER.info("RouteShot: window asked {}x{}, now {}x{} (framebuffer {}x{})", width, height, w[0], h[0], window.getWidth(),
              window.getHeight());
        }
    }

    private static void setup(final Minecraft mc)
    {
        // The window may not take its size on the first try (still settling after the world opened)
        resize(mc);
        if (!pending.isDone())
        {
            return;
        }
        pending.join();
        pending = null;
        index = 0;
        phase = Phase.ROUTES;
    }

    private static void readPoses(final Path file) throws IOException
    {
        for (final String line : Files.readAllLines(file, StandardCharsets.UTF_8))
        {
            final String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#"))
            {
                continue;
            }
            final String[] p = trimmed.split("\\s+");
            if (p.length != 6)
            {
                report.add("ERROR pose ignored, expected name x y z yaw pitch: " + trimmed);
                continue;
            }
            poses.add(new Pose(p[0], Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
              Float.parseFloat(p[4]), Float.parseFloat(p[5])));
        }
    }

    private static void routes(final Minecraft mc) throws IOException
    {
        final IntegratedServer server = mc.getSingleplayerServer();
        if (pending != null)
        {
            if (!pending.isDone())
            {
                return;
            }
            report.add(String.valueOf(pending.join()));
            pending = null;
            index++;
        }
        if (index >= routes.size() && clickIndex < clicks.size())
        {
            if (clickTimer > 0)
            {
                clickTimer--;
                return;
            }
            final int[] click = clicks.get(clickIndex++);
            final UUID id = mc.player.getUUID();
            report.add("click " + click[0] + ", " + click[1] + (click[2] != 0 ? " sneaking: " : ": ")
                         + server.submit(() -> RouteDev.click(server.overworld(), server.getPlayerList().getPlayer(id), click[0], click[1], click[2] != 0)).join());
            clickTimer = clickWait;
            return;
        }
        if (index >= routes.size() && clickTimer > 0)
        {
            clickTimer--;
            return;
        }
        if (index >= routes.size())
        {
            // The poses each route proposed for itself (its bridges, its tunnels, a few views along it)
            final Path auto = dir().resolve(RouteDev.AUTO_SHOTS);
            if (Files.exists(auto))
            {
                readPoses(auto);
            }
            index = 0;
            ticks = 0;
            phase = Phase.MOVE;
            return;
        }
        final BlockPos[] ends = routes.get(index);
        pending = server.submit(() -> RouteDev.build(server.overworld(), ends[0], ends[1], show)).thenCompose(future -> future)
          .exceptionally(e -> {
              InterregneMod.LOGGER.error("RouteShot: route failed", e);
              return "ERROR route " + ends[0].toShortString() + " -> " + ends[1].toShortString() + ": " + e;
          });
    }

    private static void move(final Minecraft mc)
    {
        if (index >= poses.size())
        {
            phase = Phase.DONE;
            return;
        }
        final IntegratedServer server = mc.getSingleplayerServer();
        final Pose pose = poses.get(index);
        final UUID id = mc.player.getUUID();
        server.execute(() -> {
            final ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null)
            {
                // The pose is where the camera is: the player's feet go one eye height lower
                player.teleportTo(server.overworld(), pose.x(), pose.y() - player.getEyeHeight(), pose.z(), offAxis(pose.yaw()), pose.pitch());
            }
        });
        ticks = 0;
        stable = 0;
        lastSections = -1;
        lastChunks = -1;
        phase = Phase.WAIT;
    }

    /**
     * The sky is a fan of eight triangles: looking exactly along one of its seams (a multiple of 45 degrees) draws a
     * one-pixel line of horizon colour up the middle of the picture. A quarter of a degree aside, it is gone.
     */
    private static float offAxis(final float yaw)
    {
        final float off = Math.abs(yaw % 45.0F);
        return off < 0.25F || off > 44.75F ? yaw + 0.25F : yaw;
    }

    private static void capture(final Minecraft mc) throws IOException
    {
        if (ticks == 2)
        {
            resize(mc);
        }
        if (mc.screen != null)
        {
            mc.setScreen(null);
        }
        final int sections = mc.levelRenderer.countRenderedSections();
        final int chunks = mc.level.getChunkSource().getLoadedChunksCount();
        if (sections == lastSections && chunks == lastChunks && sections > 0 && mc.levelRenderer.hasRenderedAllSections())
        {
            stable++;
        }
        else
        {
            stable = 0;
        }
        lastSections = sections;
        lastChunks = chunks;
        final boolean timedOut = ticks >= SHOT_TIMEOUT_TICKS;
        if (!timedOut && (ticks < settle || stable < STABLE_TICKS))
        {
            return;
        }
        final Pose pose = poses.get(index);
        final String name = pose.name() + ".png";
        mc.getToasts().clear();
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget()))
        {
            image.writeToFile(dir().resolve("out").resolve(name));
        }
        report.add(name + "  " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight() + ", " + ticks + " ticks, " + sections + " sections"
                     + (timedOut ? "  (TIMEOUT: chunks still arriving)" : ""));
        index++;
        phase = Phase.MOVE;
    }

    private static void finish(final Minecraft mc)
    {
        if (optionsSaved)
        {
            mc.options.hideGui = savedHideGui;
            mc.options.pauseOnLostFocus = savedPause;
            mc.options.cloudStatus().set(savedClouds);
            mc.options.renderDistance().set(savedDistance);
            GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), savedWidth, savedHeight);
            mc.resizeDisplay();
            optionsSaved = false;
        }
        final IntegratedServer server = mc.getSingleplayerServer();
        if (home != null && server != null && mc.player != null)
        {
            final UUID id = mc.player.getUUID();
            final double[] at = home;
            server.submit(() -> {
                final ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null)
                {
                    player.setGameMode(GameType.CREATIVE);
                    player.teleportTo(server.overworld(), at[0], at[1], at[2], (float) at[3], (float) at[4]);
                }
            }).join();
        }
        report.add("ran in " + (System.currentTimeMillis() - started) / 1000 + " s");
        try
        {
            Files.createDirectories(dir());
            Files.write(dir().resolve("done.txt"), report, StandardCharsets.UTF_8);
        }
        catch (final IOException e)
        {
            InterregneMod.LOGGER.error("RouteShot could not write its report", e);
        }
        InterregneMod.LOGGER.info("RouteShot done: {} lines", report.size());
        pending = null;
        phase = Phase.IDLE;
        if (quit)
        {
            mc.stop();
        }
    }
}
