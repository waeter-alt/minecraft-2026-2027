package fr.kodistan.interregne.client.war;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.network.SiegeBannerSyncPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;

/**
 * What makes a siege banner impossible to miss: a light beam in the attacker's colour rising from its mast, a glowing
 * ring on the ground marking its capture zone (it fills like a gauge as the defenders take it, blinks while they do,
 * holds still when an attacker contests it, fades out when the banner falls), and a marker on the screen's edge with
 * the distance.
 * <p>
 * Drawn with the vanilla additive "lightning" render type, positions and colours only: no shader of ours, no
 * post-processing pass, so a player's shader pack (Iris) handles it like lightning and is never overridden.
 */
@EventBusSubscriber(modid = InterregneMod.MODID, value = Dist.CLIENT)
public final class SiegeBannerFx
{
    private static final int SEGMENTS = 120;
    private static final float BAND = 0.85F;
    private static final float CURTAIN = 1.3F;
    private static final float MAST_TOP = 14.8F;
    private static final float BEAM_HEIGHT = 320F;
    private static final long FADE_MS = 2500;

    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;
    private static boolean matrices;
    private static long lastFrame;

    /** Ground heights around each ring, refreshed every second (only loaded chunks know their ground). */
    private static final Map<BlockPos, float[]> GROUND = new HashMap<>();
    private static long groundAt;

    private SiegeBannerFx()
    {
    }

    @SubscribeEvent
    public static void onLogout(final ClientPlayerNetworkEvent.LoggingOut event)
    {
        ClientSiegeBanners.clear();
        GROUND.clear();
    }

    @SubscribeEvent
    public static void onRender(final RenderLevelStageEvent event)
    {
        if (ClientSiegeBanners.isEmpty())
        {
            return;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY)
        {
            PROJECTION.set(event.getProjectionMatrix());
            VIEW.set(event.getModelViewMatrix());
            camera = event.getCamera().getPosition();
            matrices = true;
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES)
        {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        final ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD)
        {
            return;
        }
        final long nowMs = System.currentTimeMillis();
        ClientSiegeBanners.ease(lastFrame == 0 ? 0F : Math.min(0.25F, (nowMs - lastFrame) / 1000F));
        lastFrame = nowMs;
        final float t = (level.getGameTime() % 24000L + event.getPartialTick().getGameTimeDeltaPartialTick(false)) / 20F;
        final Vec3 cam = event.getCamera().getPosition();
        if (nowMs - groundAt > 1000)
        {
            groundAt = nowMs;
            GROUND.clear();
        }

        final PoseStack pose = event.getPoseStack();
        final Matrix4f m = pose.last().pose();
        final MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        final VertexConsumer out = buffers.getBuffer(RenderType.lightning());
        final float far = mc.options.getEffectiveRenderDistance() * 16F * 3F;
        for (final ClientSiegeBanners.Shown shown : ClientSiegeBanners.all())
        {
            final float fade = fade(shown, nowMs);
            if (fade <= 0F)
            {
                continue;
            }
            final SiegeBannerSyncPayload.Entry e = shown.entry;
            final float[] rgb = rgb(e.color());
            final double dx = e.pos().getX() + 0.5 - cam.x;
            final double dz = e.pos().getZ() + 0.5 - cam.z;
            final double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 160)
            {
                ring(out, m, level, shown, rgb, (float) (e.pos().getX() + 0.5 - cam.x), (float) (e.pos().getY() - cam.y),
                  (float) (e.pos().getZ() + 0.5 - cam.z), t, fade);
            }
            // Beyond the far plane the beam is drawn closer along the same line, scaled, so it looks the same
            final float scale = dist > far ? (float) (far / dist) : 1F;
            beam(out, m, rgb, (float) (dx * scale), (float) ((e.pos().getY() + MAST_TOP - cam.y) * scale), (float) (dz * scale), scale, shown, t, fade);
        }
        buffers.endBatch(RenderType.lightning());
    }

    private static float fade(final ClientSiegeBanners.Shown shown, final long nowMs)
    {
        if (shown.fellAt < 0)
        {
            return 1F;
        }
        return 1F - Mth.clamp((nowMs - shown.fellAt) / (float) FADE_MS, 0F, 1F);
    }

    private static float[] rgb(final int color)
    {
        return new float[] {((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F};
    }

    // --- Ring ---

    private static void ring(final VertexConsumer out, final Matrix4f m, final ClientLevel level, final ClientSiegeBanners.Shown shown,
      final float[] rgb, final float cx, final float cy, final float cz, final float t, final float fade)
    {
        final SiegeBannerSyncPayload.Entry e = shown.entry;
        final float r = e.radius();
        final float[] ground = GROUND.computeIfAbsent(e.pos(), p -> sampleGround(level, p, r));
        final byte state = e.state();
        final float pulse = 0.8F + 0.2F * Mth.sin(t * 2.2F);
        final float blink = state == SiegeBannerSyncPayload.CAPTURING ? 0.65F + 0.35F * Mth.sin(t * 11F) : 1F;
        final float filled = shown.progress * Mth.TWO_PI;
        for (int i = 0; i < SEGMENTS; i++)
        {
            final float a0 = i * Mth.TWO_PI / SEGMENTS;
            final float a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
            final float mid = (a0 + a1) / 2F;
            // Runes: bright dashes slowly turning around the ring
            final float rune = 0.55F + 0.45F * Mth.clamp(Mth.sin(mid * 18F - t * 1.3F) * 3F, 0F, 1F);
            final boolean taken = mid < filled;
            // Additive light on bright grass needs a whitish core to read at all
            float[] c = mix(rgb, new float[] {1F, 1F, 1F}, 0.3F);
            float alpha = 0.9F * pulse * rune;
            if (taken)
            {
                c = mix(rgb, new float[] {1F, 0.96F, 0.78F}, 0.65F);
                alpha = 0.95F * blink;
            }
            else if (state == SiegeBannerSyncPayload.CONTESTED)
            {
                c = mix(rgb, new float[] {0.85F, 0.9F, 1F}, 0.35F);
                alpha = 0.6F;
            }
            alpha *= fade;
            final float y0 = cy + ground[i] - e.pos().getY() + 0.06F;
            final float y1 = cy + ground[(i + 1) % SEGMENTS] - e.pos().getY() + 0.06F;
            final float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            // Flat band: soft inner edge, bright middle, soft outer edge
            for (final float[] band : new float[][] {{r - BAND, 0F, r - 0.12F, 1F}, {r - 0.12F, 1F, r + 0.12F, 1F}, {r + 0.12F, 1F, r + BAND, 0F}})
            {
                final float ra = band[0], aa = band[1] * alpha, rb = band[2], ab = band[3] * alpha;
                vertex(out, m, cx + c0 * ra, y0, cz + s0 * ra, c, aa);
                vertex(out, m, cx + c0 * rb, y0, cz + s0 * rb, c, ab);
                vertex(out, m, cx + c1 * rb, y1, cz + s1 * rb, c, ab);
                vertex(out, m, cx + c1 * ra, y1, cz + s1 * ra, c, aa);
                vertex(out, m, cx + c1 * ra, y1, cz + s1 * ra, c, aa);
                vertex(out, m, cx + c1 * rb, y1, cz + s1 * rb, c, ab);
                vertex(out, m, cx + c0 * rb, y0, cz + s0 * rb, c, ab);
                vertex(out, m, cx + c0 * ra, y0, cz + s0 * ra, c, aa);
            }
            // Light curtain rising from the ring, both faces
            final float ca = alpha * 0.6F;
            vertex(out, m, cx + c0 * r, y0, cz + s0 * r, c, ca);
            vertex(out, m, cx + c1 * r, y1, cz + s1 * r, c, ca);
            vertex(out, m, cx + c1 * r, y1 + CURTAIN, cz + s1 * r, c, 0F);
            vertex(out, m, cx + c0 * r, y0 + CURTAIN, cz + s0 * r, c, 0F);
            vertex(out, m, cx + c0 * r, y0 + CURTAIN, cz + s0 * r, c, 0F);
            vertex(out, m, cx + c1 * r, y1 + CURTAIN, cz + s1 * r, c, 0F);
            vertex(out, m, cx + c1 * r, y1, cz + s1 * r, c, ca);
            vertex(out, m, cx + c0 * r, y0, cz + s0 * r, c, ca);
        }
    }

    private static float[] sampleGround(final ClientLevel level, final BlockPos center, final float r)
    {
        final float[] ground = new float[SEGMENTS];
        for (int i = 0; i < SEGMENTS; i++)
        {
            final float a = (i + 0.5F) * Mth.TWO_PI / SEGMENTS;
            final int x = Mth.floor(center.getX() + 0.5 + Mth.cos(a) * r);
            final int z = Mth.floor(center.getZ() + 0.5 + Mth.sin(a) * r);
            final boolean loaded = level.hasChunk(x >> 4, z >> 4);
            // The client only receives the MOTION_BLOCKING and WORLD_SURFACE heightmaps from the server
            ground[i] = loaded ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) : center.getY();
            if (Math.abs(ground[i] - center.getY()) > 6)
            {
                ground[i] = center.getY();
            }
        }
        return ground;
    }

    // --- Beam ---

    private static void beam(final VertexConsumer out, final Matrix4f m, final float[] rgb, final float x, final float y, final float z,
      final float scale, final ClientSiegeBanners.Shown shown, final float t, final float fade)
    {
        final float len = (float) Math.sqrt(x * x + z * z);
        // Facing the camera around the vertical axis
        final float rx = len < 1e-3F ? 1F : -z / len;
        final float rz = len < 1e-3F ? 0F : x / len;
        float glow = 0.75F + 0.25F * Mth.sin(t * 1.7F);
        if (shown.entry.state() == SiegeBannerSyncPayload.CAPTURING)
        {
            glow *= 0.45F + 0.55F * (Mth.sin(t * 11F) > 0 ? 1F : 0.25F);
        }
        // Right next to it (or straight above) a camera-facing beam turns into a smear across the view
        glow *= fade * Mth.clamp((len / scale - 1.5F) / 3F, 0F, 1F);
        if (glow <= 0F)
        {
            return;
        }
        final float[] core = mix(rgb, new float[] {1F, 1F, 1F}, 0.55F);
        final float coreW = 0.22F * scale;
        final float haloW = 1.25F * scale;
        final float h = BEAM_HEIGHT * scale;
        final int steps = 32;
        for (int s = 0; s < steps; s++)
        {
            final float ya = y + h * s / steps;
            final float yb = y + h * (s + 1) / steps;
            final float fa = glow * (1F - (float) s / steps) * near(x, ya, z, scale);
            final float fb = glow * (1F - (float) (s + 1) / steps) * near(x, yb, z, scale);
            strip(out, m, x, z, rx, rz, -coreW, coreW, ya, yb, core, fa, fa, fb, fb);
            strip(out, m, x, z, rx, rz, -haloW, -coreW, ya, yb, rgb, 0F, fa * 0.6F, fb * 0.6F, 0F);
            strip(out, m, x, z, rx, rz, coreW, haloW, ya, yb, rgb, fa * 0.6F, 0F, 0F, fb * 0.6F);
        }
    }

    /** The beam fades out where it passes close to the camera (looking up or down along it would smear it across the view). */
    private static float near(final float x, final float y, final float z, final float scale)
    {
        final float d = (float) Math.sqrt(x * x + y * y + z * z) / scale;
        return Mth.clamp((d - 8F) / 14F, 0F, 1F);
    }

    /** A vertical quad across the beam from offset {@code u0} to {@code u1}; alphas: bottom-left, bottom-right, top-right, top-left. */
    private static void strip(final VertexConsumer out, final Matrix4f m, final float x, final float z, final float rx, final float rz,
      final float u0, final float u1, final float ya, final float yb, final float[] c, final float a0, final float a1, final float a2, final float a3)
    {
        vertex(out, m, x + rx * u0, ya, z + rz * u0, c, a0);
        vertex(out, m, x + rx * u1, ya, z + rz * u1, c, a1);
        vertex(out, m, x + rx * u1, yb, z + rz * u1, c, a2);
        vertex(out, m, x + rx * u0, yb, z + rz * u0, c, a3);
        vertex(out, m, x + rx * u0, yb, z + rz * u0, c, a3);
        vertex(out, m, x + rx * u1, yb, z + rz * u1, c, a2);
        vertex(out, m, x + rx * u1, ya, z + rz * u1, c, a1);
        vertex(out, m, x + rx * u0, ya, z + rz * u0, c, a0);
    }

    private static void vertex(final VertexConsumer out, final Matrix4f m, final float x, final float y, final float z, final float[] c, final float a)
    {
        out.addVertex(m, x, y, z).setColor(c[0], c[1], c[2], Mth.clamp(a, 0F, 1F));
    }

    private static float[] mix(final float[] a, final float[] b, final float k)
    {
        return new float[] {a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k};
    }

    // --- Screen marker ---

    @SubscribeEvent
    public static void onRegisterLayers(final RegisterGuiLayersEvent event)
    {
        event.registerBelow(VanillaGuiLayers.CHAT, ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "siege_banner_marker"), SiegeBannerFx::marker);
    }

    private static void marker(final GuiGraphics g, final DeltaTracker delta)
    {
        final Minecraft mc = Minecraft.getInstance();
        if (!matrices || ClientSiegeBanners.isEmpty() || mc.player == null || mc.level == null || mc.options.hideGui
              || mc.level.dimension() != Level.OVERWORLD)
        {
            return;
        }
        final int w = g.guiWidth();
        final int h = g.guiHeight();
        final int margin = 14;
        final long nowMs = System.currentTimeMillis();
        for (final ClientSiegeBanners.Shown shown : ClientSiegeBanners.all())
        {
            final float fade = fade(shown, nowMs);
            if (fade <= 0F)
            {
                continue;
            }
            final SiegeBannerSyncPayload.Entry e = shown.entry;
            final Vec3 target = new Vec3(e.pos().getX() + 0.5, e.pos().getY() + 18, e.pos().getZ() + 0.5);
            final double dist = mc.player.position().distanceTo(target);
            // Close enough to see the banner itself: the marker would only hide it
            if (dist < 32)
            {
                continue;
            }
            final Vector4f v = new Vector4f((float) (target.x - camera.x), (float) (target.y - camera.y), (float) (target.z - camera.z), 1F);
            VIEW.transform(v);
            PROJECTION.transform(v);
            float sx;
            float sy;
            final boolean behind = v.w <= 0.01F;
            if (behind)
            {
                sx = -v.x;
                sy = v.y;
            }
            else
            {
                sx = v.x / v.w;
                sy = v.y / v.w;
            }
            final boolean inside = !behind && Math.abs(sx) <= 1F && Math.abs(sy) <= 1F;
            if (!inside)
            {
                // Push the point onto the screen's edge, keeping its direction from the centre
                final float k = Math.max(Math.abs(sx), Math.abs(sy));
                sx = k < 1e-4F ? 0F : sx / k;
                sy = k < 1e-4F ? -1F : sy / k;
            }
            final int px = Mth.clamp(Math.round((sx + 1F) / 2F * w), margin, w - margin);
            final int py = Mth.clamp(Math.round((1F - sy) / 2F * h), margin, h - margin - 10);
            drawMarker(g, mc, px, py, e, shown.progress, (int) Math.round(dist), fade);
        }
    }

    private static void drawMarker(final GuiGraphics g, final Minecraft mc, final int x, final int y, final SiegeBannerSyncPayload.Entry e,
      final float progress, final int dist, final float fade)
    {
        final int a = Math.round(255 * fade) << 24;
        final int color = a | (e.color() & 0xFFFFFF);
        final int dark = a | 0x1A1A1A;
        // A little gonfanon: dark outline, mast, pointed cloth in the attacker's colour, gold band
        g.fill(x - 4, y - 9, x + 5, y + 4, a | 0x101010);
        g.fill(x - 1, y - 10, x, y + 6, dark);
        g.fill(x - 3, y - 8, x + 4, y + 1, color);
        g.fill(x - 2, y + 1, x + 3, y + 2, color);
        g.fill(x - 1, y + 2, x + 2, y + 3, color);
        g.fill(x, y - 8, x + 1, y + 3, a | 0xE8BE46);
        final String text = dist + " m";
        final int tw = mc.font.width(text);
        g.drawString(mc.font, text, x - tw / 2, y + 7, a | 0xFFFFFF, true);
        if (e.state() == SiegeBannerSyncPayload.CAPTURING || e.state() == SiegeBannerSyncPayload.CONTESTED || progress > 0.01F)
        {
            final int bw = 18;
            g.fill(x - bw / 2 - 1, y + 17, x + bw / 2 + 1, y + 20, a | 0x101010);
            g.fill(x - bw / 2, y + 18, x - bw / 2 + Math.round(bw * progress), y + 19,
              e.state() == SiegeBannerSyncPayload.CONTESTED ? a | 0xBFD0E0 : a | 0xFFF2C0);
        }
    }
}
