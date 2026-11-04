package fr.kodistan.interregne.client.hut;

import com.ldtteam.structurize.api.constants.Constants;
import com.ldtteam.structurize.storage.rendering.RenderingCache;
import com.minecolonies.core.client.gui.WindowSupplies;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.mixin.client.AbstractBlueprintManipulationWindowAccessor;
import fr.kodistan.interregne.mixin.client.WindowSuppliesInvoker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * Placing the starting camp or ship: a small parchment panel at the side, the world left clear to see the plan, with
 * buttons to move it, turn it, flip it, and set it down. It drives MineColonies' own placement window, kept unseen, so
 * the checks and the placement stay theirs; the plan's keys work too.
 */
public final class SuppliesScreen extends Screen
{
    private static final String PREVIEW = "supplies";
    private static final int PANEL_W = 150;
    private static final int PANEL_H = 176;

    private record Hit(int x, int y, int width, int height, Runnable action)
    {
    }

    private final WindowSupplies window;
    private final AbstractBlueprintManipulationWindowAccessor controls;
    private final boolean camp;
    private final List<Hit> hits = new ArrayList<>();
    private int x0;
    private int y0;

    private SuppliesScreen(final WindowSupplies window)
    {
        super(Component.literal("Placement"));
        this.window = window;
        this.controls = (AbstractBlueprintManipulationWindowAccessor) window;
        this.camp = controls.interregne$groundstyle() == Constants.GROUNDSTYLE_LEGACY_CAMP;
    }

    public static void open(final WindowSupplies window)
    {
        if (RenderingCache.getOrCreateBlueprintPreviewData(PREVIEW).getPos() == null)
        {
            // As MineColonies does: nothing to place without a position
            Minecraft.getInstance().player.displayClientMessage(Component.translatable("structurize.gui.missing.pos"), false);
            return;
        }
        Minecraft.getInstance().setScreen(new SuppliesScreen(window));
    }

    @Override
    protected void init()
    {
        x0 = width - PANEL_W - 8;
        y0 = (height - PANEL_H) / 2;
    }

    @Override
    public void renderBackground(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick)
    {
        // No veil: the plan in the world must stay visible
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    private boolean loaded()
    {
        return RenderingCache.getOrCreateBlueprintPreviewData(PREVIEW).getBlueprint() != null;
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(graphics, mouseX, mouseY, partialTick);
        hits.clear();
        Ui.plate(graphics, x0, y0, x0 + PANEL_W, y0 + PANEL_H, Palette.PAPER, Palette.EDGE, Palette.PAPER_LIGHT);
        final int x = x0 + 8;
        final int width = PANEL_W - 16;
        graphics.drawString(font, Component.literal(camp ? "Camp de départ" : "Navire de départ").withStyle(ChatFormatting.BOLD), x, y0 + 7, Palette.INK, false);
        graphics.fill(x0 + 4, y0 + 19, x0 + PANEL_W - 4, y0 + 20, Palette.RULE);
        int y = Ui.wrapped(graphics, font, Component.literal(loaded() ? "Place le plan, puis pose-le : tout se construit d'un coup." : "Chargement du plan…"), x, y0 + 25,
          width, Palette.MUTED, 10) + 4;

        // Moving: forward, back and sides as the player faces
        final int pad = x + (width - 3 * 22) / 2;
        button(graphics, mouseX, mouseY, pad + 22, y, 20, "↑", "Avancer", controls::interregne$forward);
        button(graphics, mouseX, mouseY, pad, y + 18, 20, "←", "À gauche", controls::interregne$left);
        button(graphics, mouseX, mouseY, pad + 44, y + 18, 20, "→", "À droite", controls::interregne$right);
        button(graphics, mouseX, mouseY, pad + 22, y + 36, 20, "↓", "Reculer", controls::interregne$back);
        y += 58;
        final int half = (width - 3) / 2;
        button(graphics, mouseX, mouseY, x, y, half, "Monter", null, controls::interregne$up);
        button(graphics, mouseX, mouseY, x + half + 3, y, half, "Descendre", null, controls::interregne$down);
        y += 17;
        final int third = (width - 6) / 3;
        button(graphics, mouseX, mouseY, x, y, third, "↺", "Tourner à gauche", controls::interregne$rotateLeft);
        button(graphics, mouseX, mouseY, x + third + 3, y, third, "↻", "Tourner à droite", controls::interregne$rotateRight);
        button(graphics, mouseX, mouseY, x + 2 * third + 6, y, third, "⇄", "Retourner en miroir", controls::interregne$mirror);
        y += 17;
        button(graphics, mouseX, mouseY, x, y, width, "Changer de style", null, () -> ((WindowSuppliesInvoker) window).interregne$switchPack());

        // Set down or give up
        final int by = y0 + PANEL_H - 22;
        final boolean overPlace = Ui.inside(mouseX, mouseY, x, by, half, 16);
        Ui.button(graphics, font, x, by, half, 16, "Poser ici", Ui.ButtonStyle.PRIMARY, loaded(), overPlace);
        if (loaded())
        {
            hits.add(new Hit(x, by, half, 16, this::place));
        }
        final boolean overCancel = Ui.inside(mouseX, mouseY, x + half + 3, by, half, 16);
        Ui.button(graphics, font, x + half + 3, by, half, 16, "Annuler", Ui.ButtonStyle.SECONDARY, true, overCancel);
        hits.add(new Hit(x + half + 3, by, half, 16, this::onClose));
        if (!loaded() && overPlace)
        {
            graphics.renderTooltip(font, Component.literal("Le plan se charge encore."), mouseX, mouseY);
        }
    }

    private void button(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width, final String label,
      final String tip, final Runnable action)
    {
        final boolean over = Ui.inside(mouseX, mouseY, x, y, width, 15);
        Ui.button(graphics, font, x, y, width, 15, label, Ui.ButtonStyle.SECONDARY, loaded(), over);
        if (loaded())
        {
            hits.add(new Hit(x, y, width, 15, action));
        }
        if (over && tip != null)
        {
            graphics.renderTooltip(font, Component.literal(tip), mouseX, mouseY);
        }
    }

    /**
     * MineColonies checks the ground and places the camp; on a refusal it outlines the faulty blocks and the panel
     * stays for another try.
     */
    private void place()
    {
        controls.interregne$confirm();
        if (RenderingCache.getOrCreateBlueprintPreviewData(PREVIEW).getBlueprint() == null)
        {
            minecraft.setScreen(null);
        }
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (button == 0)
        {
            for (final Hit hit : List.copyOf(hits))
            {
                if (Ui.inside(mouseX, mouseY, hit.x(), hit.y(), hit.width(), hit.height()))
                {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    hit.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers)
    {
        // Escape gives up; the plan's own keys (move, turn, place) work as in MineColonies' window
        if (keyCode != 256 && loaded() && window.onUnhandledKeyTyped(0, keyCode))
        {
            if (RenderingCache.getOrCreateBlueprintPreviewData(PREVIEW).getBlueprint() == null)
            {
                minecraft.setScreen(null);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose()
    {
        controls.interregne$cancel();
        minecraft.setScreen(null);
    }
}
