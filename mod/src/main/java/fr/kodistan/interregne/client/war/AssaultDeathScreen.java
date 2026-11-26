package fr.kodistan.interregne.client.war;

import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.network.AssaultDeathPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Replaces the vanilla death screen for a death during an assault: a countdown, where the player will come back, and
 * the respawn button only once the wait is over. Meanwhile a broken crown mends itself on its pedestal, in step with the wait.
 */
@EventBusSubscriber(modid = InterregneMod.MODID, value = Dist.CLIENT)
public final class AssaultDeathScreen extends Screen
{
    private static final int BUTTON_W = 160;
    private static final int BUTTON_H = 24;

    /** The wait the server announced for the death in progress, or null outside one. */
    @Nullable
    private static AssaultDeathPayload pending;
    private static long pendingAt;

    private final long   readyAt;
    private final int    totalMs;
    private final String where;
    private final boolean demo;
    private boolean rang;

    private AssaultDeathScreen(final long readyAt, final int totalMs, final String where, final boolean demo)
    {
        super(Component.literal("Tombé au combat"));
        this.readyAt = readyAt;
        this.totalMs = totalMs;
        this.where = where;
        this.demo = demo;
    }

    /** The server's word on a death in an assault: arrives just before the death screen opens. */
    public static void receive(final AssaultDeathPayload payload)
    {
        pending = payload;
        pendingAt = Util.getMillis();
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DeathScreen)
        {
            minecraft.setScreen(fromPending());
        }
    }

    /** UiShot: the screen with {@code leftMs} still to wait out of {@code totalMs}. */
    public static void openDemo(final int leftMs, final int totalMs, final String where)
    {
        Minecraft.getInstance().setScreen(new AssaultDeathScreen(Util.getMillis() + leftMs, totalMs, where, true));
    }

    private static AssaultDeathScreen fromPending()
    {
        final int totalMs = pending.waitTicks() * 50;
        return new AssaultDeathScreen(pendingAt + totalMs, totalMs, pending.where(), false);
    }

    @SubscribeEvent
    public static void onScreenOpening(final ScreenEvent.Opening event)
    {
        if (pending != null && event.getNewScreen() instanceof DeathScreen)
        {
            event.setNewScreen(fromPending());
        }
    }

    /** Respawned (by our button or any other way): the wait is spent, a later death outside an assault gets the vanilla screen. */
    @SubscribeEvent
    public static void onRespawned(final ClientPlayerNetworkEvent.Clone event)
    {
        pending = null;
    }

    @SubscribeEvent
    public static void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event)
    {
        pending = null;
    }

    private long leftMs()
    {
        return Math.max(0, readyAt - Util.getMillis());
    }

    private boolean ready()
    {
        return leftMs() == 0;
    }

    // The crown's textures (mod/tools/gen_crown_textures.py): 64x48, the pedestal 64x18
    private static final int CROWN_W = 64;
    private static final int CROWN_H = 48;
    private static final int PEDESTAL_H = 18;
    /** Crown row its bottom rests on: the pedestal's cushion starts there. */
    private static final int CROWN_SEAT = 43;
    private static final ResourceLocation[] SHARDS = new ResourceLocation[6];
    private static final ResourceLocation[] SHINE = new ResourceLocation[10];
    private static final ResourceLocation GOLD = crownTexture("gold");
    private static final ResourceLocation CRACKS = crownTexture("cracks");
    private static final ResourceLocation GLOW = crownTexture("glow");
    private static final ResourceLocation SLOT = crownTexture("slot");
    private static final ResourceLocation PEDESTAL = crownTexture("pedestal");
    /** Each shard's centre in texture pixels (printed by the generator): the pivot it spins around in flight. */
    private static final float[][] SHARD_CENTERS = {{14.6f, 23.5f}, {32.4f, 15.9f}, {50.5f, 23.5f}, {14.8f, 38.0f}, {33.3f, 35.4f}, {51.0f, 38.1f}};
    /** Landing order: the band first, the points on top of it. */
    private static final int[] ORDER = {4, 3, 5, 1, 0, 2};
    private static final int[] FLIGHT_DX = {-60, 70, -40, 50, -80, 35};
    private static final int[] FLIGHT_SPIN = {-120, 90, -70, 150, -100, 60};
    /** Fractions of the wait: shards fly in until 0.63, the cracks glow from 0.72, the crown is whole by 0.95. */
    private static final float FLY_START = 0.03f;
    private static final float FLY_STEP = 0.085f;
    private static final float FLY_TIME = 0.17f;
    private static final float GLOW_START = 0.72f;
    /** Sparkles around the gilded crown: x, y in texture pixels, delay in ms. */
    private static final int[][] SPARKLES = {{-6, 10, 0}, {70, 12, 60}, {32, -8, 120}, {10, -2, 200}, {56, -4, 260}, {-10, 34, 320},
      {74, 36, 380}, {22, 50, 160}, {44, 52, 240}, {4, 22, 440}, {62, 26, 500}, {32, 20, 560}};

    static
    {
        for (int i = 0; i < SHARDS.length; i++)
        {
            SHARDS[i] = crownTexture("shard_" + i);
        }
        for (int i = 0; i < SHINE.length; i++)
        {
            SHINE[i] = crownTexture("shine_" + i);
        }
    }

    private int landed;
    private boolean chimed;

    private static ResourceLocation crownTexture(final String name)
    {
        return ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "textures/gui/crown/" + name + ".png");
    }

    /** How far the wait has gone, 0 to 1. */
    private float progress()
    {
        return totalMs <= 0 ? 1f : Mth.clamp(1f - (float) leftMs() / totalMs, 0f, 1f);
    }

    /** One shard's flight, 0 (below the screen) to 1 (in place). */
    private static float flight(final float progress, final int rank)
    {
        return Mth.clamp((progress - FLY_START - rank * FLY_STEP) / FLY_TIME, 0f, 1f);
    }

    private static float ramp(final float value, final float from, final float to)
    {
        return Mth.clamp((value - from) / (to - from), 0f, 1f);
    }

    /** The crown's scale and where everything sits, recomputed per frame so it follows the window. */
    private record Layout(float scale, int titleY, int crownX, int crownY, int numberY, int barY, int lineY, int buttonY) {}

    private Layout layout()
    {
        // Title 16, number 24, bar + line + button 51, gaps 30: what's left goes to the crown and its pedestal
        final int rest = 16 + 24 + 51 + 30;
        final int fit = Mth.clamp((height - rest) / (CROWN_SEAT + PEDESTAL_H), 1, 3);
        // 80 % of the size that fits, rounded to whole screen pixels per texture pixel so the pixel art stays even
        final double gui = minecraft.getWindow().getGuiScale();
        final float scale = (float) (Math.max(1, Math.round(fit * gui * 0.8)) / gui);
        final int crownH = Math.round(scale * (CROWN_SEAT + PEDESTAL_H));
        final int top = Math.max(4, (height - rest - crownH) / 2);
        final int crownY = top + 16 + 6 - Math.round(scale * 2);
        final int numberY = crownY + crownH + 6;
        final int barY = numberY + 24 + 6;
        final int lineY = barY + 9;
        return new Layout(scale, top, Math.round((width - CROWN_W * scale) / 2), crownY, numberY, barY, lineY, lineY + 16);
    }

    private int buttonX()
    {
        return (width - BUTTON_W) / 2;
    }

    private int buttonY()
    {
        return layout().buttonY();
    }

    @Override
    public void tick()
    {
        final float progress = progress();
        while (landed < ORDER.length && flight(progress, landed) >= 1f)
        {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_HIT, 0.9f + landed * 0.08f, 0.8f));
            landed++;
        }
        if (!chimed && progress >= GLOW_START)
        {
            chimed = true;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1f));
        }
        if (ready() && !rang)
        {
            rang = true;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.2f));
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.4f, 0.4f));
        }
    }

    @Override
    public void renderBackground(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick)
    {
        graphics.fillGradient(0, 0, width, height, 0xB0200000, 0xE0100404);
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(graphics, mouseX, mouseY, partialTick);
        final Layout at = layout();
        final int cx = width / 2;
        drawScaled(graphics, "Vous êtes tombé au combat", cx, at.titleY(), width >= 260 ? 2 : 1, Palette.WHITE);

        final long left = leftMs();
        final float progress = progress();
        renderCrown(graphics, at, progress, left == 0 ? Util.getMillis() - readyAt : -1);

        if (left > 0)
        {
            drawScaled(graphics, String.valueOf((int) Math.ceil(left / 1000.0)), cx, at.numberY(), 3, Palette.GOLD_TEXT);
        }

        final int barW = Math.min(180, width - 32);
        graphics.fill(cx - barW / 2 - 1, at.barY() - 1, cx + barW / 2 + 1, at.barY() + 4, 0xFF2A0E0E);
        graphics.fill(cx - barW / 2, at.barY(), cx - barW / 2 + Math.round(barW * progress), at.barY() + 3, left > 0 ? 0xFFB08A3A : Palette.GREEN_LIGHT);

        final String line = "Réapparition à " + where;
        graphics.drawCenteredString(font, Ui.fit(font, line, width - 16), cx, at.lineY(), 0xFFD8C8B0);

        if (left == 0)
        {
            final boolean hover = Ui.inside(mouseX, mouseY, buttonX(), at.buttonY(), BUTTON_W, BUTTON_H);
            Ui.solidButton(graphics, font, buttonX(), at.buttonY(), BUTTON_W, BUTTON_H, "Réapparaître", Ui.State.OK, hover);
        }
    }

    /**
     * The broken crown mending itself on its pedestal: shards fly up from below the screen and settle, dark cracks
     * at their joins, the cracks glow then fade, and at the end of the wait the crown turns gold with a shine and sparkles.
     *
     * @param sinceReady ms since the wait ended, or -1 while it runs.
     */
    private void renderCrown(final GuiGraphics graphics, final Layout at, final float progress, final long sinceReady)
    {
        final float s = at.scale();
        final int x = at.crownX();
        final int y = at.crownY();
        RenderSystem.enableBlend();
        blit(graphics, PEDESTAL, x, y + Math.round(CROWN_SEAT * s), CROWN_W, PEDESTAL_H, s, 1f);
        blit(graphics, SLOT, x, y, CROWN_W, CROWN_H, s, 1f - ramp(progress, 0.45f, 0.63f));

        final float gold = sinceReady < 0 ? 0f : Mth.clamp(sinceReady / 400f, 0f, 1f);
        if (gold < 1f)
        {
            for (int rank = 0; rank < ORDER.length; rank++)
            {
                renderShard(graphics, at, ORDER[rank], flight(progress, rank));
            }
            final float dark = ramp(progress, FLY_START + 5 * FLY_STEP + FLY_TIME - 0.02f, 0.66f) * (1f - ramp(progress, 0.86f, 0.95f));
            blit(graphics, CRACKS, x, y, CROWN_W, CROWN_H, s, dark);
            final float envelope = ramp(progress, GLOW_START, 0.78f) * (1f - ramp(progress, 0.88f, 0.95f));
            final float pulse = 0.65f + 0.35f * Mth.sin(Util.getMillis() / 90f);
            blit(graphics, GLOW, x, y, CROWN_W, CROWN_H, s, envelope * pulse);
        }
        if (gold > 0f)
        {
            blit(graphics, GOLD, x, y, CROWN_W, CROWN_H, s, gold);
            final int frame = (int) (sinceReady / 60);
            if (frame < SHINE.length)
            {
                blit(graphics, SHINE[frame], x, y, CROWN_W, CROWN_H, s, 1f);
            }
            renderSparkles(graphics, at, sinceReady);
        }
        RenderSystem.disableBlend();
    }

    private void renderShard(final GuiGraphics graphics, final Layout at, final int shard, final float flight)
    {
        final float s = at.scale();
        final float centerX = SHARD_CENTERS[shard][0] * s;
        final float centerY = SHARD_CENTERS[shard][1] * s;
        // Ease out with a small overshoot, so each shard snaps into place
        final float t = flight - 1f;
        final float eased = 1f + 2.2f * t * t * t + 1.2f * t * t;
        final float fromBelow = height + 24 - (at.crownY() + centerY);
        final float dx = FLIGHT_DX[shard] * (1f - eased);
        final float dy = fromBelow * (1f - eased) - Mth.sin(flight * Mth.PI) * 18f;
        final float spin = FLIGHT_SPIN[shard] * (1f - eased);
        graphics.pose().pushPose();
        graphics.pose().translate(at.crownX() + centerX + dx, at.crownY() + centerY + dy, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(spin));
        graphics.pose().translate(-centerX, -centerY, 0);
        graphics.pose().scale(s, s, 1f);
        graphics.blit(SHARDS[shard], 0, 0, 0, 0, CROWN_W, CROWN_H, CROWN_W, CROWN_H);
        graphics.pose().popPose();
    }

    private void renderSparkles(final GuiGraphics graphics, final Layout at, final long sinceReady)
    {
        final float s = at.scale();
        for (final int[] sparkle : SPARKLES)
        {
            final long age = sinceReady - sparkle[2];
            if (age < 0 || age > 900)
            {
                continue;
            }
            final float life = age / 900f;
            final int alpha = (int) (255 * (1f - life));
            final int arm = Math.max(1, Math.round((life < 0.3f ? 2 : 1) * s));
            final int px = at.crownX() + Math.round(sparkle[0] * s);
            final int py = at.crownY() + Math.round(sparkle[1] * s - life * 6 * s);
            final int color = (alpha << 24) | 0xFFF2A8;
            graphics.fill(px - arm, py, px + arm + 1, py + 1, color);
            graphics.fill(px, py - arm, px + 1, py + arm + 1, color);
        }
    }

    private static void blit(final GuiGraphics graphics, final ResourceLocation texture, final int x, final int y, final int w, final int h,
      final float scale, final float alpha)
    {
        if (alpha <= 0.01f)
        {
            return;
        }
        graphics.setColor(1f, 1f, 1f, alpha);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.blit(texture, 0, 0, 0, 0, w, h, w, h);
        graphics.pose().popPose();
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    private void drawScaled(final GuiGraphics graphics, final String text, final int cx, final int y, final int scale, final int color)
    {
        graphics.pose().pushPose();
        graphics.pose().translate(cx, y, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, text, -font.width(text) / 2, 0, color, true);
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (ready() && button == 0 && Ui.inside(mouseX, mouseY, buttonX(), buttonY(), BUTTON_W, BUTTON_H))
        {
            respawn();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers)
    {
        if (ready() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_KP_ENTER))
        {
            respawn();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void respawn()
    {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1f));
        if (!demo)
        {
            pending = null;
            if (minecraft.player != null)
            {
                minecraft.player.respawn();
            }
        }
        minecraft.setScreen(null);
    }

    @Override
    public boolean shouldCloseOnEsc()
    {
        return demo;
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
