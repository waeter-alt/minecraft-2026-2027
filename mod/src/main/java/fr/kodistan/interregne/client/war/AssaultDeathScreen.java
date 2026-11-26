package fr.kodistan.interregne.client.war;

import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.network.AssaultDeathPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Replaces the vanilla death screen for a death during an assault: a countdown, where the player will come back, and
 * the respawn button only once the wait is over. A simple first version; the broken crown comes later.
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

    private int buttonX()
    {
        return (width - BUTTON_W) / 2;
    }

    private int buttonY()
    {
        return height / 2 + 46;
    }

    @Override
    public void tick()
    {
        if (ready() && !rang)
        {
            rang = true;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.2f));
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
        final int cx = width / 2;
        // Integer scales only: the text stays pixel-sharp at every GUI scale
        final int titleScale = width >= 260 ? 2 : 1;
        final int bigScale = height >= 220 ? 4 : 3;

        drawScaled(graphics, "Vous êtes tombé au combat", cx, height / 2 - 70, titleScale, Palette.WHITE);

        final long left = leftMs();
        final float elapsed = totalMs <= 0 ? 1f : 1f - (float) left / totalMs;
        if (left > 0)
        {
            final String seconds = String.valueOf((int) Math.ceil(left / 1000.0));
            drawScaled(graphics, seconds, cx, height / 2 - 30, bigScale, Palette.GOLD_TEXT);
        }
        else
        {
            drawScaled(graphics, "Prêt", cx, height / 2 - 26, bigScale - 1, Palette.GOLD_TEXT);
        }

        final int barW = Math.min(180, width - 32);
        final int barY = height / 2 + 12;
        graphics.fill(cx - barW / 2 - 1, barY - 1, cx + barW / 2 + 1, barY + 4, 0xFF2A0E0E);
        graphics.fill(cx - barW / 2, barY, cx - barW / 2 + Math.round(barW * elapsed), barY + 3, left > 0 ? 0xFFB08A3A : Palette.GREEN_LIGHT);

        final String line = "Réapparition à " + where;
        graphics.drawCenteredString(font, Ui.fit(font, line, width - 16), cx, barY + 9, 0xFFD8C8B0);

        if (left == 0)
        {
            final boolean hover = Ui.inside(mouseX, mouseY, buttonX(), buttonY(), BUTTON_W, BUTTON_H);
            Ui.solidButton(graphics, font, buttonX(), buttonY(), BUTTON_W, BUTTON_H, "Réapparaître", Ui.State.OK, hover);
        }
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
