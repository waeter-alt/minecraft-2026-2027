package fr.kodistan.interregne.client.hut;

import com.ldtteam.structurize.client.gui.WindowSwitchPack;
import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.core.client.gui.WindowBannerPicker;
import com.minecolonies.core.client.gui.townhall.WindowMainPage;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import com.minecolonies.core.network.messages.server.colony.ColonyNameStyleMessage;
import com.minecolonies.core.network.messages.server.colony.ColonyStructureStyleMessage;
import com.minecolonies.core.network.messages.server.colony.ColonyTextureStyleMessage;
import com.minecolonies.core.network.messages.server.colony.TeamColonyColorChangeMessage;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.minecolonies.core.event.TextureReloadListener.TEXTURE_PACKS;

/**
 * How the colony looks: its colour, its flag, its buildings' style, its citizens' look and names, with a preview of
 * the colony's name in its colour first. Replaces the town hall's welcome page; the Patreon gate is gone, every style
 * is open to whoever runs the colony.
 */
public final class AppearanceScreen extends ModuleScreen
{
    private static final String[] COLOR_NAMES = {"Noir", "Bleu nuit", "Vert sombre", "Bleu canard", "Rouge sombre", "Pourpre", "Or", "Gris",
      "Gris sombre", "Bleu", "Vert", "Cyan", "Rouge", "Mauve", "Jaune", "Blanc"};

    private final BuildingTownHall.View townHall;

    private AppearanceScreen(final BuildingTownHall.View townHall, @Nullable final Screen parent)
    {
        super("Apparence", townHall, parent, 400);
        this.townHall = townHall;
    }

    public static void open(final BuildingTownHall.View townHall, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new AppearanceScreen(townHall, parent));
    }

    private boolean allowed()
    {
        return building.getColony().getPermissions().hasPermission(minecraft.player, Action.EDIT_PERMISSIONS);
    }

    private static String pretty(final String id)
    {
        final String text = id.replace('_', ' ').replace('-', ' ');
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1).toLowerCase(Locale.ROOT);
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width)
    {
        // The preview: the colony's name in its colour, and what the rest of the page sets
        final IColonyView colony = building.getColony();
        final ChatFormatting color = colony.getTeamColonyColor();
        final int rgb = color.getColor() == null ? Palette.INK : 0xFF000000 | color.getColor();
        Ui.card(graphics, x, y, width, 34, false, false, false);
        graphics.fill(x + 1, y + 1, x + 5, y + 33, rgb);
        final Component name = Component.literal(colony.getName()).withStyle(ChatFormatting.BOLD);
        // A pale colour is outlined so it stays readable on the parchment
        if (color == ChatFormatting.WHITE || color == ChatFormatting.YELLOW || color == ChatFormatting.AQUA)
        {
            graphics.drawString(font, name, x + 12, y + 7, Palette.EDGE, false);
            graphics.drawString(font, name, x + 11, y + 6, rgb, false);
        }
        else
        {
            graphics.drawString(font, name, x + 11, y + 6, rgb, false);
        }
        graphics.drawString(font, fit("Bâtiments : " + pretty(colony.getStructurePack()) + " · Citoyens : " + pretty(colony.getTextureStyleId()), width - 16), x + 11,
          y + 20, Palette.MUTED, false);
        if (!allowed())
        {
            Ui.chip(graphics, font, x + width - 6 - font.width("Lecture seule") - 8, y + 5, "Lecture seule", Ui.State.WAIT);
        }
        return y + 40;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final IColonyView colony = building.getColony();
        final boolean allowed = allowed();
        final String why = allowed ? null : "Réservé au chef de la colonie et à ses officiers.";

        // The colour, as swatches
        Ui.label(graphics, font, "COULEUR DE LA COLONIE", x, y);
        y += 11;
        final List<ChatFormatting> colors = java.util.Arrays.stream(ChatFormatting.values()).filter(ChatFormatting::isColor).toList();
        for (int i = 0; i < colors.size(); i++)
        {
            final ChatFormatting each = colors.get(i);
            final int sx = x + i * 19;
            final boolean current = each == colony.getTeamColonyColor();
            final boolean over = Ui.inside(mouseX, mouseY, sx, y, 17, 17);
            Ui.plate(graphics, sx, y, sx + 17, y + 17, 0xFF000000 | each.getColor(), current ? Palette.SELECTED : Palette.EDGE, 0xFF000000 | each.getColor());
            if (current || over)
            {
                graphics.renderOutline(sx - 1, y - 1, 19, 19, current ? Palette.SELECTED : Palette.INK);
            }
            if (over)
            {
                hint = i < COLOR_NAMES.length ? COLOR_NAMES[i] + (allowed ? "" : " · " + why) : null;
            }
            if (allowed && !current)
            {
                addHit(sx, y, 17, 17, () -> new TeamColonyColorChangeMessage(each.ordinal(), townHall).sendToServer());
            }
        }
        y += 25;

        // Flag and buildings' style, one line each with its button
        y = line(graphics, mouseX, mouseY, x, y, width, "Drapeau", "Le blason sur les bannières et les gardes.", "Dessiner", allowed, why, () -> {
            final Screen picker = new WindowBannerPicker(colony, new WindowMainPage(townHall), new AtomicBoolean(true));
            minecraft.setScreen(picker);
        });
        y = line(graphics, mouseX, mouseY, x, y, width, "Style des bâtiments", pretty(colony.getStructurePack()) + " : les plans proposés à la construction.", "Changer",
          allowed, why, () -> new WindowSwitchPack(() -> {
              colony.setStructurePack(StructurePacks.selectedPack.getName());
              new ColonyStructureStyleMessage(colony, StructurePacks.selectedPack.getName()).sendToServer();
              return new WindowMainPage(townHall);
          }).open());
        y += 4;

        // The citizens' look and names
        Ui.label(graphics, font, "ALLURE DES CITOYENS", x, y);
        y += 11;
        y = choices(graphics, mouseX, mouseY, x, y, width, TEXTURE_PACKS, colony.getTextureStyleId(), allowed, why,
          id -> new ColonyTextureStyleMessage(colony, id).sendToServer());
        y += 6;
        Ui.label(graphics, font, "NOMS DES CITOYENS", x, y);
        y += 11;
        y = choices(graphics, mouseX, mouseY, x, y, width, colony.getNameFileIds(), colony.getNameStyle(), allowed, why,
          id -> new ColonyNameStyleMessage(colony, id).sendToServer());
        return y;
    }

    /**
     * A setting on a card: its name in bold, what it does, the button that changes it on the right.
     */
    private int line(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width, final String name, final String text,
      final String action, final boolean allowed, @Nullable final String why, final Runnable run)
    {
        Ui.card(graphics, x, y, width, 26, false, false, false);
        graphics.drawString(font, Component.literal(name).withStyle(ChatFormatting.BOLD), x + 6, y + 4, Palette.INK, false);
        graphics.drawString(font, fit(text, width - buttonWidth(action) - 20), x + 6, y + 15, Palette.MUTED, false);
        button(graphics, mouseX, mouseY, x + width - 6 - buttonWidth(action), y + 6, action, Ui.ButtonStyle.PRIMARY, allowed, why, run);
        return y + 29;
    }

    /**
     * Every choice as a chip, wrapping, the current one in green.
     */
    private int choices(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width, final List<String> ids,
      final String current, final boolean allowed, @Nullable final String why, final Consumer<String> choose)
    {
        int cx = x;
        for (final String id : ids)
        {
            final String label = pretty(id);
            final int cw = font.width(label) + 10;
            if (cx + cw > x + width)
            {
                cx = x;
                y += 16;
            }
            final boolean on = id.equals(current);
            final boolean over = Ui.inside(mouseX, mouseY, cx, y, cw, 14);
            if (on)
            {
                Ui.plate(graphics, cx, y, cx + cw, y + 14, Palette.GREEN, Palette.EDGE, Palette.GREEN_LIGHT);
            }
            else
            {
                Ui.plate(graphics, cx, y, cx + cw, y + 14, over && allowed ? Palette.CARD_HOVER : Palette.PAPER_LIGHT, Palette.RULE, Palette.PAPER_LIGHT);
            }
            graphics.drawString(font, label, cx + 5, y + 3, on ? Palette.GREEN_TEXT : allowed ? Palette.INK : Palette.MUTED, false);
            if (over && why != null)
            {
                hint = why;
            }
            if (allowed && !on)
            {
                addHit(cx, y, cw, 14, () -> choose.accept(id));
            }
            cx += cw + 3;
        }
        return y + 16;
    }
}
