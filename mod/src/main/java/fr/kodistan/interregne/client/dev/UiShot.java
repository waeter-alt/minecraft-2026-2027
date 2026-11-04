package fr.kodistan.interregne.client.dev;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.client.hut.ColonyHubScreen;
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
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Dev-only screen capture, so screens can be checked without anyone at the keyboard.
 * <p>
 * Drop {@code uishot/request.properties} in the client's game directory:
 * <pre>
 * world=Test_citadelle            (optional: opened from the title screen when no world is loaded)
 * screen=hub:intendance           (which screen, see {@link UiScenarios})
 * scenarios=payable,short         (optional: default all the screen's scenarios)
 * sizes=1553x1011@2,854x480@2     (optional: window size and GUI scale per capture)
 * hover=-1,-1                     (optional: forced mouse position in GUI pixels, default off-screen)
 * </pre>
 * Each scenario is captured at each size into {@code uishot/out/<scenario>_<w>x<h>_s<scale>.png}, then
 * {@code uishot/done.txt} lists the files and any error. The window and GUI scale are put back afterwards.
 * Fake data comes from {@link DevOverrides}, which nothing sets outside a capture.
 */
@EventBusSubscriber(modid = InterregneMod.MODID, value = Dist.CLIENT)
public final class UiShot
{
    private static final String DEFAULT_SIZES = "1553x1011@2,1553x1011@3,1553x1011@4,854x480@2";
    private static final int SETTLE_TICKS = 8;
    private static final int LOAD_TIMEOUT_TICKS = 20 * 120;

    private enum Phase
    {
        IDLE, LOADING, APPLY, WAIT, DONE
    }

    private record Size(int width, int height, int scale)
    {
    }

    private record Job(String scenario, Size size)
    {
    }

    private static Phase phase = Phase.IDLE;
    private static int ticks;
    private static String world;
    private static String screen;
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
                case WAIT ->
                {
                    if (ticks >= SETTLE_TICKS)
                    {
                        capture(mc);
                    }
                }
                case DONE -> finish(mc);
            }
        }
        catch (final Exception e)
        {
            report.add("ERROR " + e);
            InterregneMod.LOGGER.error("UiShot failed", e);
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
        jobs.clear();
        jobIndex = 0;
        world = props.getProperty("world", "").trim();
        screen = props.getProperty("screen", "hub:intendance").trim();
        worldAsked = false;
        DevOverrides.hover = parseHover(props.getProperty("hover", "-1000,-1000"));

        final List<String> scenarios = props.containsKey("scenarios")
                                         ? Arrays.stream(props.getProperty("scenarios").split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
                                         : UiScenarios.scenarios(screen);
        final List<Size> sizes = new ArrayList<>();
        for (final String spec : props.getProperty("sizes", DEFAULT_SIZES).split(","))
        {
            final String[] parts = spec.trim().split("[x@]");
            sizes.add(new Size(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
        }
        for (final String scenario : scenarios)
        {
            for (final Size size : sizes)
            {
                jobs.add(new Job(scenario, size));
            }
        }

        final Window window = mc.getWindow();
        savedWidth = window.getWidth();
        savedHeight = window.getHeight();
        savedScale = mc.options.guiScale().get();
        ticks = 0;
        phase = Phase.LOADING;
        InterregneMod.LOGGER.info("UiShot: {} captures of {}", jobs.size(), screen);
    }

    private static int[] parseHover(final String spec)
    {
        final String[] parts = spec.split(",");
        return new int[] {Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
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
            if (colony == null || colony.getTownHall() == null)
            {
                return;
            }
            // Let the rest of the colony data and the HUD arrive
            ticks = LOAD_TIMEOUT_TICKS - 60;
            return;
        }
        if (ticks >= LOAD_TIMEOUT_TICKS - 1)
        {
            ticks = 0;
            phase = Phase.APPLY;
        }
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
        UiScenarios.apply(screen, job.scenario(), colony);
        UiScenarios.open(screen, job.scenario(), colony);
        ticks = 0;
        phase = Phase.WAIT;
    }

    private static void capture(final Minecraft mc) throws IOException
    {
        final Job job = jobs.get(jobIndex);
        if (ticks == SETTLE_TICKS)
        {
            // A late window resize may land after the screen opened: lay it out once more, then let a few frames draw
            mc.resizeDisplay();
            return;
        }
        if (ticks < SETTLE_TICKS + 3)
        {
            return;
        }
        final Window window = mc.getWindow();
        final String name = job.scenario() + "_" + window.getWidth() + "x" + window.getHeight() + "_s" + (int) window.getGuiScale() + ".png";
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget()))
        {
            image.writeToFile(dir().resolve("out").resolve(name));
        }
        final String asked = job.size().width() + "x" + job.size().height() + "@" + job.size().scale();
        report.add(name + (name.contains(job.size().width() + "x" + job.size().height() + "_s" + job.size().scale()) ? "" : "   (asked " + asked + ")"));
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
        InterregneMod.LOGGER.info("UiShot done: {} files", report.size());
        colony = null;
        phase = Phase.IDLE;
    }
}
