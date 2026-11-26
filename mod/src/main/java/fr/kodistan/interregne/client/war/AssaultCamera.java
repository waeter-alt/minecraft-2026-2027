package fr.kodistan.interregne.client.war;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import fr.kodistan.interregne.InterregneMod;

/**
 * The spectator camera of the assault wait screen: high above the attacked colony, looking down on the fight, slowly
 * circling on its own; the player can turn it by dragging and zoom with the wheel. Applied by CameraMixin, the
 * death tilt is cancelled by GameRendererMixin while it runs.
 */
@EventBusSubscriber(modid = InterregneMod.MODID, value = Dist.CLIENT)
public final class AssaultCamera
{
    private static final float MIN_PITCH = 30;
    private static final float MAX_PITCH = 85;
    private static final double MIN_DISTANCE = 24;
    private static final double MAX_DISTANCE = 120;
    /** Degrees per second of the idle circling, and how long after the last drag it starts again. */
    private static final float SPIN = 4f;
    private static final long IDLE_MS = 2500;

    private static boolean active;
    private static double focusX;
    private static double focusY;
    private static double focusZ;
    private static float yaw;
    private static float pitch;
    private static double distance;
    private static long lastInput;
    private static long lastFrame;

    private AssaultCamera()
    {
    }

    /** The camera is far above the player: no first-person hand in the picture. */
    @SubscribeEvent
    public static void onRenderHand(final RenderHandEvent event)
    {
        if (active)
        {
            event.setCanceled(true);
        }
    }

    static void start(final BlockPos focus, final float startYaw)
    {
        active = true;
        focusX = focus.getX() + 0.5;
        focusY = focus.getY();
        focusZ = focus.getZ() + 0.5;
        yaw = startYaw;
        pitch = 55;
        distance = 64;
        lastInput = 0;
        lastFrame = Util.getMillis();
    }

    static void stop()
    {
        active = false;
    }

    public static boolean active()
    {
        return active;
    }

    static void drag(final double dx, final double dy)
    {
        yaw += (float) (dx * 0.5);
        pitch = (float) Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch + dy * 0.5));
        lastInput = Util.getMillis();
    }

    static void zoom(final double steps)
    {
        distance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, distance * Math.pow(0.88, steps)));
        lastInput = Util.getMillis();
    }

    /** Called once per frame, before the camera is placed: advances the idle circling. */
    public static void frame()
    {
        final long now = Util.getMillis();
        if (now - lastInput > IDLE_MS)
        {
            yaw += SPIN * (now - lastFrame) / 1000f;
        }
        lastFrame = now;
    }

    public static float yaw()
    {
        return yaw;
    }

    public static float pitch()
    {
        return pitch;
    }

    /** Back from the focus point along the view, like the wall planner's camera. */
    public static Vec3 position()
    {
        final double p = Math.toRadians(pitch);
        final double y = Math.toRadians(yaw);
        final double horizontal = Math.cos(p) * distance;
        return new Vec3(focusX + Math.sin(y) * horizontal, focusY + Math.sin(p) * distance, focusZ - Math.cos(y) * horizontal);
    }
}
