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
    /**
     * The colony's limits, as unit edges {x, z, dir}: dir 0 runs from (x, z) to (x + 1, z), dir 1 from (x, z) to (x, z + 1).
     */
    private static java.util.List<int[]> border = java.util.List.of();

    /**
     * Shows the colony's limits on the ground.
     */
    static void border(final java.util.List<int[]> edges)
    {
        border = edges;
    }

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
        border = java.util.List.of();
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
            probe = Terrain.probe(minecraft.level, x, z, OWN.contains(key) ? fr.kodistan.interregne.walls.WallBlocks::isWallBlock : null);
            if (probe.loaded())
            {
                PROBES.put(key, probe);
            }
        }
        return probe;
    }

    /**
     * Cells of the colony's own built wall: the terrain is read through its blocks there, as the ground it was built on.
     */
    private static final java.util.Set<Long> OWN = new java.util.HashSet<>();

    static void own(final java.util.Collection<Long> cells)
    {
        OWN.clear();
        OWN.addAll(cells);
        PROBES.clear();
    }

    static void forgetTerrain()
    {
        PROBES.clear();
    }

    static void show(@Nullable final WallLayout layout, @Nullable final WallTrace.Point at, final boolean closing, @Nullable final WallTrace.Point gate)
    {
        ghost = layout;
        cursor = at;
        cursorClosing = closing;
        gatePreview = gate;
    }

    @Nullable
    private static WallTrace.Point gatePreview;

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
        if (ghost == null && cursor == null && border.isEmpty())
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

        drawBorder(out, matrix);
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
                tower(out, matrix, tower, color(tower.status(), true));
            }
            for (final WallLayout.Gate gate : ghost.gates())
            {
                final boolean preview = gatePreview != null && gatePreview.x() == gate.x() && gatePreview.z() == gate.z();
                gate(out, matrix, gate, gate.status() != WallLayout.Status.OK ? color(gate.status(), false) : preview ? GATE_PREVIEW : GATE);
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

    private static final float[] BORDER = {0.62f, 1.0f, 0.86f, 0.55f};
    private static final float BORDER_HEIGHT = 2.5f;

    /**
     * The colony's limits: a low glowing curtain standing on the ground, fading upward, unlike anything the wall uses.
     */
    private static void drawBorder(final VertexConsumer out, final Matrix4f m)
    {
        for (final int[] edge : border)
        {
            final int x = edge[0];
            final int z = edge[1];
            final boolean alongX = edge[2] == 0;
            final Terrain.Probe a = probe(x, z);
            final Terrain.Probe b = alongX ? probe(x, z - 1) : probe(x - 1, z);
            if (!a.loaded() && !b.loaded())
            {
                continue;
            }
            final float y0 = Math.max(a.loaded() ? a.ground() : Integer.MIN_VALUE, b.loaded() ? b.ground() : Integer.MIN_VALUE) + 1.02f;
            final float y1 = y0 + BORDER_HEIGHT;
            final float x1 = alongX ? x + 1 : x;
            final float z1 = alongX ? z : z + 1;
            out.addVertex(m, x, y0, z).setColor(BORDER[0], BORDER[1], BORDER[2], BORDER[3]);
            out.addVertex(m, x1, y0, z1).setColor(BORDER[0], BORDER[1], BORDER[2], BORDER[3]);
            out.addVertex(m, x1, y1, z1).setColor(BORDER[0], BORDER[1], BORDER[2], 0f);
            out.addVertex(m, x, y1, z).setColor(BORDER[0], BORDER[1], BORDER[2], 0f);
            // A bright line along the foot, readable from high up
            final float w = 0.12f;
            if (alongX)
            {
                quad(out, m, BORDER, x, y0 + 0.01f, z - w, x, y0 + 0.01f, z + w, x1, y0 + 0.01f, z + w, x1, y0 + 0.01f, z - w);
            }
            else
            {
                quad(out, m, BORDER, x - w, y0 + 0.01f, z, x - w, y0 + 0.01f, z1, x + w, y0 + 0.01f, z1, x + w, y0 + 0.01f, z);
            }
        }
    }

    /**
     * A round tower as it will stand: its cells, outer sides only, and merlons on every other block of its rim.
     */
    private static void tower(final VertexConsumer out, final Matrix4f m, final WallLayout.Tower tower, final float[] color)
    {
        final float y0 = tower.bottom();
        final float y1 = tower.top() + 1;
        for (int dx = 0; dx < tower.size(); dx++)
        {
            for (int dz = 0; dz < tower.size(); dz++)
            {
                final int x = tower.x() + dx;
                final int z = tower.z() + dz;
                if (!tower.covers(x, z))
                {
                    continue;
                }
                quad(out, m, color, x, y1, z, x, y1, z + 1, x + 1, y1, z + 1, x + 1, y1, z);
                final boolean north = !tower.covers(x, z - 1);
                final boolean south = !tower.covers(x, z + 1);
                final boolean west = !tower.covers(x - 1, z);
                final boolean east = !tower.covers(x + 1, z);
                if (north)
                {
                    wall(out, m, color, y0, y1, x, z, x + 1, z);
                }
                if (south)
                {
                    wall(out, m, color, y0, y1, x + 1, z + 1, x, z + 1);
                }
                if (west)
                {
                    wall(out, m, color, y0, y1, x, z + 1, x, z);
                }
                if (east)
                {
                    wall(out, m, color, y0, y1, x + 1, z, x + 1, z + 1);
                }
                if ((north || south || west || east) && ((x + z) & 1) == 0)
                {
                    box(out, m, x, y1, z, x + 1, y1 + 1, z + 1, color);
                }
            }
        }
    }

    private static final float[] GATE = {1.0f, 0.80f, 0.25f, 0.50f};
    private static final float[] GATE_PREVIEW = {1.0f, 0.93f, 0.55f, 0.45f};

    /**
     * A gatehouse: its two turrets, the lintel over the passage and a thin strip on the passage floor.
     */
    private static void gate(final VertexConsumer out, final Matrix4f m, final WallLayout.Gate gate, final float[] color)
    {
        final int half = WallLayout.GATE_HALF;
        final int inner = WallLayout.PASSAGE_HALF;
        final int depth = gate.depth();
        // Corners of a span along the wall (from a to b inclusive) and across it, as a box
        gateBox(out, m, gate, -half, -inner - 1, -depth, depth, gate.floor() + 1, gate.crest() + 3, color);
        gateBox(out, m, gate, inner + 1, half, -depth, depth, gate.floor() + 1, gate.crest() + 3, color);
        gateBox(out, m, gate, -inner, inner, -depth, depth, gate.crest(), gate.crest() + 2, color);
        gateBox(out, m, gate, -inner, inner, -depth, depth, gate.floor() + 1, gate.floor() + 1.1f, color);
    }

    private static void gateBox(final VertexConsumer out, final Matrix4f m, final WallLayout.Gate gate, final int along0, final int along1,
      final int across0, final int across1, final float y0, final float y1, final float[] color)
    {
        final int ax = gate.x() + along0 * gate.ux() - across0 * gate.uz();
        final int az = gate.z() + along0 * gate.uz() + across0 * gate.ux();
        final int bx = gate.x() + along1 * gate.ux() - across1 * gate.uz();
        final int bz = gate.z() + along1 * gate.uz() + across1 * gate.ux();
        box(out, m, Math.min(ax, bx), y0, Math.min(az, bz), Math.max(ax, bx) + 1, y1, Math.max(az, bz) + 1, color);
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
