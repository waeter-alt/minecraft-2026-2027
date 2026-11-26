package fr.kodistan.interregne.client.war;

import fr.kodistan.interregne.InterregneMod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * The spectator camera of the assault wait screen, steered like the wall planner's: it starts above the attacked
 * colony's town hall, the movement keys slide it over the ground, dragging turns it, the wheel zooms. It stays
 * within range of the town hall. Applied by CameraMixin; the death tilt is cancelled by GameRendererMixin meanwhile.
 */
@EventBusSubscriber(modid = InterregneMod.MODID, value = Dist.CLIENT)
public final class AssaultCamera
{
    private static final float MIN_PITCH = 30;
    private static final float MAX_PITCH = 88;
    private static final double MIN_DISTANCE = 12;
    private static final double MAX_DISTANCE = 110;
    /** How far from the town hall the camera may roam: the assault zone. */
    private static final double RANGE = 160;

    private static boolean active;
    private static double anchorX;
    private static double anchorZ;
    private static double focusX;
    private static double focusY;
    private static double focusZ;
    private static float yaw;
    private static float pitch;
    private static double distance;

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
        anchorX = focus.getX() + 0.5;
        anchorZ = focus.getZ() + 0.5;
        focusX = anchorX;
        focusY = focus.getY();
        focusZ = anchorZ;
        yaw = startYaw;
        pitch = 58;
        distance = 48;
    }

    static void stop()
    {
        active = false;
    }

    public static boolean active()
    {
        return active;
    }

    static void rotate(final double dYaw, final double dPitch)
    {
        yaw = (float) (yaw + dYaw);
        pitch = (float) Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch + dPitch));
    }

    static void zoom(final double factor)
    {
        distance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, distance * factor));
    }

    static double distance()
    {
        return distance;
    }

    /**
     * Slides the focus over the ground: forward along the view, right across it, kept within range of the town hall,
     * its height easing toward the ground below.
     */
    static void move(final double forward, final double right, final double seconds)
    {
        final double y = Math.toRadians(yaw);
        focusX += -Math.sin(y) * forward - Math.cos(y) * right;
        focusZ += Math.cos(y) * forward - Math.sin(y) * right;
        final double dx = focusX - anchorX;
        final double dz = focusZ - anchorZ;
        final double far = Math.sqrt(dx * dx + dz * dz);
        if (far > RANGE)
        {
            focusX = anchorX + dx * RANGE / far;
            focusZ = anchorZ + dz * RANGE / far;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        final int bx = (int) Math.floor(focusX);
        final int bz = (int) Math.floor(focusZ);
        if (minecraft.level != null && minecraft.level.hasChunk(bx >> 4, bz >> 4))
        {
            // MOTION_BLOCKING: one of the heightmaps the server sends to clients (NO_LEAVES isn't, it reads the world's floor)
            final int ground = minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
            if (ground > minecraft.level.getMinBuildHeight())
            {
                focusY += (ground - focusY) * Math.min(1, seconds * 4);
            }
        }
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
