package fr.kodistan.interregne.client.dev;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
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
import java.util.stream.Stream;

/**
 * Dev-only screen capture, so screens can be checked without anyone at the keyboard.
 * <p>
 * Drop {@code uishot/request.properties} in the client's game directory:
 * <pre>
 * world=Test_citadelle            (optional: opened from the title screen when no world is loaded)
 * screen=hub:intendance,block:all (what to capture, see {@link UiScenarios}; "list" only reports the building types)
 * only=intendance-short           (optional: comma-separated target names to keep)
 * sizes=1553x1011@4,854x480@2     (optional: window size and GUI scale per capture)
 * hover=x,y                       (optional: forced mouse position in GUI pixels, default off-screen)
 * </pre>
 * Each target is captured at each size into {@code uishot/out/<target>_<w>x<h>_s<scale>.png}, then
 * {@code uishot/done.txt} lists the files and any error. The window and GUI scale are put back afterwards.
 * Fake data comes from {@link DevOverrides}, which nothing sets outside a capture.
 */
@EventBusSubscriber(modid = InterregneMod.MODID, value = Dist.CLIENT)
public final class UiShot
{
    private static final String DEFAULT_SIZES = "1553x1011@4,1553x1011@2,854x480@2";
    private static final int SETTLE_TICKS = 8;
    private static final int LOAD_TIMEOUT_TICKS = 20 * 120;

    private enum Phase
    {
        IDLE, LOADING, APPLY, WAIT, DONE
    }

    private record Size(int width, int height, int scale)
    {
    }

    private record Job(UiScenarios.Target target, Size size)
    {
    }

    private static Phase phase = Phase.IDLE;
    private static int ticks;
    private static String world;
    private static String screen;
    private static List<String> only;
    private static final List<Size> sizes = new ArrayList<>();
    private static boolean worldAsked;
    private static final List<Job> jobs = new ArrayList<>();
    private static int jobIndex;
    private static final List<String> report = new ArrayList<>();
    private static int savedWidth;
    private static int savedHeight;
    private static int savedScale;
    private static IColonyView colony;

    private UiShot()
    {
    }

    private static Path dir()
    {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("uishot");
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
                case APPLY -> apply(mc);
                case WAIT -> capture(mc);
                case DONE -> finish(mc);
            }
        }
        catch (final Exception e)
        {
            final String where = phase == Phase.APPLY || phase == Phase.WAIT ? " at " + jobs.get(jobIndex).target().name() : "";
            report.add("ERROR" + where + " " + e);
            InterregneMod.LOGGER.error("UiShot failed", e);
            // One broken screen does not stop the others
            if (phase == Phase.APPLY || phase == Phase.WAIT)
            {
                jobIndex++;
                ticks = 0;
                phase = Phase.APPLY;
            }
            else
            {
                phase = Phase.DONE;
            }
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
        jobs.clear();
        sizes.clear();
        jobIndex = 0;
        colony = null;
        world = props.getProperty("world", "").trim();
        screen = props.getProperty("screen", "hub:intendance").trim();
        only = props.containsKey("only") ? List.of(props.getProperty("only").replace(" ", "").split(",")) : null;
        worldAsked = false;
        final String[] hover = props.getProperty("hover", "-1000,-1000").split(",");
        DevOverrides.hover = new int[] {Integer.parseInt(hover[0].trim()), Integer.parseInt(hover[1].trim())};
        for (final String spec : props.getProperty("sizes", DEFAULT_SIZES).split(","))
        {
            final String[] parts = spec.trim().split("[x@]");
            sizes.add(new Size(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
        }

        final Window window = mc.getWindow();
        savedWidth = window.getWidth();
        savedHeight = window.getHeight();
        savedScale = mc.options.guiScale().get();
        ticks = 0;
        phase = Phase.LOADING;
    }

    private static void load(final Minecraft mc)
    {
        if (ticks > LOAD_TIMEOUT_TICKS)
        {
            report.add("ERROR timed out waiting for the world and the colony");
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
        if (colony == null || colony.getTownHall() == null)
        {
            colony = IColonyManager.getInstance().getClosestColonyView(mc.level, mc.player.blockPosition());
            if (colony != null && colony.getTownHall() != null)
            {
                // Let the rest of the colony data and the HUD arrive
                ticks = LOAD_TIMEOUT_TICKS - 60;
            }
            return;
        }
        if (ticks < LOAD_TIMEOUT_TICKS - 1)
        {
            return;
        }
        if (screen.equals("list"))
        {
            report.add("Building types: " + UiScenarios.buildingTypes(colony));
            phase = Phase.DONE;
            return;
        }
        for (final UiScenarios.Target target : UiScenarios.targets(screen, colony))
        {
            if (only != null && !only.contains(target.name()))
            {
                continue;
            }
            for (final Size size : sizes)
            {
                jobs.add(new Job(target, size));
            }
        }
        InterregneMod.LOGGER.info("UiShot: {} captures of {}", jobs.size(), screen);
        ticks = 0;
        phase = Phase.APPLY;
    }

    private static void apply(final Minecraft mc)
    {
        if (jobIndex >= jobs.size())
        {
            phase = Phase.DONE;
            return;
        }
        final Job job = jobs.get(jobIndex);
        final Window window = mc.getWindow();
        if (window.getWidth() != job.size().width() || window.getHeight() != job.size().height())
        {
            GLFW.glfwRestoreWindow(window.getWindow());
            GLFW.glfwSetWindowSize(window.getWindow(), job.size().width(), job.size().height());
        }
        mc.options.guiScale().set(job.size().scale());
        mc.resizeDisplay();
        final int[] hover = DevOverrides.hover;
        DevOverrides.clear();
        DevOverrides.hover = hover;
        mc.setScreen(null);
        job.target().open().run();
        ticks = 0;
        phase = Phase.WAIT;
    }

    private static void capture(final Minecraft mc) throws IOException
    {
        final Job job = jobs.get(jobIndex);
        final int settle = SETTLE_TICKS + job.target().settleTicks();
        if (ticks == settle)
        {
            // A late window resize may land after the screen opened: lay it out once more, then let a few frames draw
            mc.resizeDisplay();
            return;
        }
        if (ticks < settle + 3)
        {
            return;
        }
        final Window window = mc.getWindow();
        final String name = job.target().name() + "_" + window.getWidth() + "x" + window.getHeight() + "_s" + (int) window.getGuiScale() + ".png";
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget()))
        {
            image.writeToFile(dir().resolve("out").resolve(name));
        }
        final boolean exact = window.getWidth() == job.size().width() && window.getHeight() == job.size().height() && (int) window.getGuiScale() == job.size().scale();
        final String screenName = mc.screen == null ? "none" : mc.screen.getClass().getSimpleName();
        report.add(name + "  [" + screenName + "]" + (exact ? "" : "  (asked " + job.size().width() + "x" + job.size().height() + "@" + job.size().scale() + ")"));
        jobIndex++;
        ticks = 0;
        phase = Phase.APPLY;
    }

    private static void finish(final Minecraft mc)
    {
        DevOverrides.clear();
        mc.setScreen(null);
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), savedWidth, savedHeight);
        mc.options.guiScale().set(savedScale);
        mc.resizeDisplay();
        try
        {
            Files.write(dir().resolve("done.txt"), report, StandardCharsets.UTF_8);
        }
        catch (final IOException e)
        {
            InterregneMod.LOGGER.error("UiShot could not write its report", e);
        }
        InterregneMod.LOGGER.info("UiShot done: {} lines", report.size());
        colony = null;
        phase = Phase.IDLE;
    }
}
