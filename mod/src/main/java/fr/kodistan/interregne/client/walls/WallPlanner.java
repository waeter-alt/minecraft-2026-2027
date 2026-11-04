package fr.kodistan.interregne.client.walls;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.walls.Terrain;
import fr.kodistan.interregne.walls.WallLayout;
import fr.kodistan.interregne.walls.WallTrace;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * The aerial wall planner's world side: the free camera above the colony (applied by CameraMixin), the mouse ray onto
 * the terrain, and the ghost of the wall drawn in the world. The screen drives it.
 */
@EventBusSubscriber(modid = InterregneMod.MODID, value = Dist.CLIENT)
public final class WallPlanner
{
    public static final float MIN_PITCH = 35;
    public static final float MAX_PITCH = 88;
    public static final double MIN_DISTANCE = 12;
    public static final double MAX_DISTANCE = 110;

    private static boolean active;
    private static double focusX;
    private static double focusY;
    private static double focusZ;
    private static float yaw;
    private static float pitch = 58;
    private static double distance = 48;

    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW = new Matrix4f();
    private static boolean matrices;

    private static final Long2ObjectOpenHashMap<Terrain.Probe> PROBES = new Long2ObjectOpenHashMap<>();

    @Nullable
    private static WallLayout ghost;
    @Nullable
    private static WallTrace.Point cursor;
    private static boolean cursorClosing;

    private WallPlanner()
    {
    }

    static void start(final double x, final double y, final double z, final float startYaw)
    {
        active = true;
        focusX = x + 0.5;
        focusY = y;
        focusZ = z + 0.5;
        yaw = startYaw;
        matrices = false;
        PROBES.clear();
        ghost = null;
        cursor = null;
    }

    static void stop()
    {
        active = false;
        ghost = null;
        cursor = null;
        PROBES.clear();
    }

    public static boolean active()
    {
        return active;
    }

    public static float yaw()
    {
        return yaw;
    }

    public static float pitch()
    {
        return pitch;
    }

    /**
     * Where the camera stands: back from the focus point along the view.
     */
    public static Vec3 cameraPosition()
    {
        final double p = Math.toRadians(pitch);
        final double y = Math.toRadians(yaw);
        final double horizontal = Math.cos(p) * distance;
        return new Vec3(focusX + Math.sin(y) * horizontal, focusY + Math.sin(p) * distance, focusZ - Math.cos(y) * horizontal);
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
     * Moves the focus on the ground plane: forward along the view, right across it. The focus stays within range of
     * the anchor (the town hall) and its height eases toward the ground below.
     */
    static void move(final double forward, final double right, final double anchorX, final double anchorZ, final double range, final double seconds)
    {
        final double y = Math.toRadians(yaw);
        focusX += -Math.sin(y) * forward - Math.cos(y) * right;
        focusZ += Math.cos(y) * forward - Math.sin(y) * right;
        final double dx = focusX - anchorX;
        final double dz = focusZ - anchorZ;
        final double far = Math.sqrt(dx * dx + dz * dz);
        if (far > range)
        {
            focusX = anchorX + dx * range / far;
            focusZ = anchorZ + dz * range / far;
        }
        final Terrain.Probe below = probe((int) Math.floor(focusX), (int) Math.floor(focusZ));
        if (below.loaded())
        {
            focusY += (below.ground() + 1 - focusY) * Math.min(1, seconds * 4);
        }
    }

    static Terrain.Probe probe(final int x, final int z)
    {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null)
        {
            return Terrain.Probe.UNKNOWN;
        }
        final long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
        Terrain.Probe probe = PROBES.get(key);
        if (probe == null)
        {
            probe = Terrain.probe(minecraft.level, x, z);
            if (probe.loaded())
            {
                PROBES.put(key, probe);
            }
        }
        return probe;
    }

    static void forgetTerrain()
    {
        PROBES.clear();
    }

    static void show(@Nullable final WallLayout layout, @Nullable final WallTrace.Point at, final boolean closing)
    {
        ghost = layout;
        cursor = at;
        cursorClosing = closing;
    }

    /**
     * The column under the mouse, given in screen fractions (0..1 from the top left), or null when it points at the
     * sky or the camera matrices are not known yet.
     */
    @Nullable
    static WallTrace.Point pick(final double fx, final double fy)
    {
        if (!matrices)
        {
            return null;
        }
        final Matrix4f inverse = new Matrix4f(PROJECTION).mul(VIEW).invert();
        final float ndcX = (float) (fx * 2 - 1);
        final float ndcY = (float) (1 - fy * 2);
        final Vector3f near = inverse.transformProject(ndcX, ndcY, -1, new Vector3f());
        final Vector3f far = inverse.transformProject(ndcX, ndcY, 1, new Vector3f());
        final Vector3f dir = new Vector3f(far).sub(near).normalize();
        final Vec3 camera = cameraPosition();
        double x = camera.x + near.x;
        double y = camera.y + near.y;
        double z = camera.z + near.z;
        final double step = 0.4;
        for (int i = 0; i < 1200; i++)
        {
            final Terrain.Probe probe = probe((int) Math.floor(x), (int) Math.floor(z));
            if (probe.loaded() && y <= probe.ground() + 1)
            {
                return new WallTrace.Point((int) Math.floor(x), (int) Math.floor(z));
            }
            x += dir.x * step;
            y += dir.y * step;
            z += dir.z * step;
        }
        return null;
    }

    @SubscribeEvent
    public static void onRender(final RenderLevelStageEvent event)
    {
        if (!active)
        {
            return;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY)
        {
            PROJECTION.set(event.getProjectionMatrix());
            VIEW.set(event.getModelViewMatrix());
            matrices = true;
        }
        else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
        {
            drawGhost(event);
        }
    }

    // --- Ghost ---

    private static final float[] OK = {0.35f, 0.70f, 1.0f, 0.38f};
    private static final float[] OK_TOWER = {0.25f, 0.55f, 0.95f, 0.45f};
    private static final float[] BLOCKED = {1.0f, 0.22f, 0.18f, 0.50f};
    private static final float[] OUTSIDE = {1.0f, 0.62f, 0.15f, 0.45f};
    private static final float[] CURSOR = {1.0f, 1.0f, 1.0f, 0.55f};
    private static final float[] CLOSING = {0.40f, 0.95f, 0.40f, 0.60f};

    private static void drawGhost(final RenderLevelStageEvent event)
    {
        if (ghost == null && cursor == null)
        {
            return;
        }
        final Vec3 camera = event.getCamera().getPosition();
        final PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        final Matrix4f matrix = pose.last().pose();
        final MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        final VertexConsumer out = buffers.getBuffer(RenderType.debugQuads());

        if (ghost != null)
        {
            final Map<Long, WallLayout.Column> byCell = new HashMap<>();
            for (final WallLayout.Column column : ghost.columns())
            {
                byCell.put(key(column.x(), column.z()), column);
            }
            for (final WallLayout.Column column : ghost.columns())
            {
                final float[] color = color(column.status(), false);
                column(out, matrix, column, byCell, color);
            }
            for (final WallLayout.Tower tower : ghost.towers())
            {
                box(out, matrix, tower.x(), tower.bottom(), tower.z(), tower.x() + tower.size(), tower.top() + 1, tower.z() + tower.size(),
                  color(tower.status(), true));
            }
        }
        if (cursor != null)
        {
            final Terrain.Probe probe = probe(cursor.x(), cursor.z());
            final float y = probe.ground() + 1;
            box(out, matrix, cursor.x() - 0.1f, y, cursor.z() - 0.1f, cursor.x() + 1.1f, y + 0.25f, cursor.z() + 1.1f, cursorClosing ? CLOSING : CURSOR);
        }
        buffers.endBatch(RenderType.debugQuads());
        pose.popPose();
    }

    private static float[] color(final WallLayout.Status status, final boolean tower)
    {
        return switch (status)
        {
            case OK -> tower ? OK_TOWER : OK;
            case BLOCKED -> BLOCKED;
            case OUTSIDE -> OUTSIDE;
        };
    }

    /**
     * A wall column: its top, and each side only where the neighbour does not cover it, so the translucent ghost
     * does not stack into a dark mush.
     */
    private static void column(final VertexConsumer out, final Matrix4f m, final WallLayout.Column c, final Map<Long, WallLayout.Column> byCell,
      final float[] color)
    {
        final float x0 = c.x();
        final float z0 = c.z();
        final float x1 = x0 + 1;
        final float z1 = z0 + 1;
        final float y0 = c.bottom();
        final float y1 = c.top() + 1;
        quad(out, m, color, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        // north (-z), south (+z), west (-x), east (+x)
        side(out, m, color, byCell.get(key(c.x(), c.z() - 1)), y0, y1, x0, z0, x1, z0);
        side(out, m, color, byCell.get(key(c.x(), c.z() + 1)), y0, y1, x1, z1, x0, z1);
        side(out, m, color, byCell.get(key(c.x() - 1, c.z())), y0, y1, x0, z1, x0, z0);
        side(out, m, color, byCell.get(key(c.x() + 1, c.z())), y0, y1, x1, z0, x1, z1);
    }

    private static void side(final VertexConsumer out, final Matrix4f m, final float[] color, @Nullable final WallLayout.Column neighbour,
      final float y0, final float y1, final float ax, final float az, final float bx, final float bz)
    {
        if (neighbour == null)
        {
            wall(out, m, color, y0, y1, ax, az, bx, bz);
            return;
        }
        final float nTop = neighbour.top() + 1;
        final float nBottom = neighbour.bottom();
        if (y1 > nTop)
        {
            wall(out, m, color, Math.max(y0, nTop), y1, ax, az, bx, bz);
        }
        if (y0 < nBottom)
        {
            wall(out, m, color, y0, Math.min(y1, nBottom), ax, az, bx, bz);
        }
    }

    private static void wall(final VertexConsumer out, final Matrix4f m, final float[] color, final float y0, final float y1, final float ax,
      final float az, final float bx, final float bz)
    {
        quad(out, m, color, ax, y0, az, bx, y0, bz, bx, y1, bz, ax, y1, az);
    }

    private static void box(final VertexConsumer out, final Matrix4f m, final float x0, final float y0, final float z0, final float x1,
      final float y1, final float z1, final float[] color)
    {
        quad(out, m, color, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(out, m, color, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        wall(out, m, color, y0, y1, x0, z0, x1, z0);
        wall(out, m, color, y0, y1, x1, z1, x0, z1);
        wall(out, m, color, y0, y1, x0, z1, x0, z0);
        wall(out, m, color, y0, y1, x1, z0, x1, z1);
    }

    private static void quad(final VertexConsumer out, final Matrix4f m, final float[] c, final float ax, final float ay, final float az,
      final float bx, final float by, final float bz, final float cx, final float cy, final float cz, final float dx, final float dy, final float dz)
    {
        out.addVertex(m, ax, ay, az).setColor(c[0], c[1], c[2], c[3]);
        out.addVertex(m, bx, by, bz).setColor(c[0], c[1], c[2], c[3]);
        out.addVertex(m, cx, cy, cz).setColor(c[0], c[1], c[2], c[3]);
        out.addVertex(m, dx, dy, dz).setColor(c[0], c[1], c[2], c[3]);
    }

    private static long key(final int x, final int z)
    {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
