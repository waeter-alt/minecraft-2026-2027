package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.buildings.modules.settings.ISetting;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.colony.buildings.moduleviews.SettingsModuleView;
import com.minecolonies.core.colony.buildings.modules.settings.BoolSetting;
import com.minecolonies.core.colony.buildings.modules.settings.GuardTaskSetting;
import com.minecolonies.core.colony.buildings.modules.settings.IntSetting;
import com.minecolonies.core.colony.buildings.modules.settings.StringSetting;
import com.minecolonies.core.network.messages.server.colony.building.TriggerSettingMessage;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A building's settings, one card each: its name, what it does, and the control right beside it (an on/off switch,
 * every choice laid out when they fit, or a number with − and +). Replaces MineColonies' settings page for every
 * building whose settings are all of these kinds; the others keep the original page.
 */
public final class SettingsScreen extends ModuleScreen
{
    private static final int WIDTH = 380;
    private static final String NAME_KEY = "com.minecolonies.coremod.setting.";
    private static final String TOOLTIP_KEY = "com.minecolonies.coremod.setting.tooltip.";

    private SettingsModuleView module;

    private SettingsScreen(final IBuildingView building, final SettingsModuleView module, @Nullable final Screen parent)
    {
        super("Réglages", building, parent, WIDTH);
        this.module = module;
    }

    @Override
    public void tick()
    {
        // The colony swaps building views on each update: follow the newest
        super.tick();
        final int id = module.getProducer().getRuntimeID();
        final SettingsModuleView view = building.getModuleViewMatching(SettingsModuleView.class, m -> m.getProducer().getRuntimeID() == id);
        if (view != null)
        {
            module = view;
        }
    }

    public static void open(final IBuildingView building, final SettingsModuleView module, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new SettingsScreen(building, module, parent));
    }

    /**
     * Whether every setting shown is one this screen knows how to draw.
     */
    public static boolean supports(final SettingsModuleView module)
    {
        for (final ISettingKey<? extends ISetting> key : module.getSettingsToShow())
        {
            final ISetting<?> setting = module.getSetting(key);
            if (!(setting instanceof BoolSetting || setting instanceof IntSetting || (setting instanceof StringSetting && !(setting instanceof GuardTaskSetting))))
            {
                return false;
            }
        }
        return true;
    }

    private static String nameOf(final ISettingKey<?> key)
    {
        return Component.translatable(NAME_KEY + key.getUniqueId()).getString();
    }

    @Nullable
    private static String descriptionOf(final ISettingKey<?> key, final ISetting<?> setting)
    {
        final String tooltip = TOOLTIP_KEY + key.getUniqueId();
        if (I18n.exists(tooltip))
        {
            return Component.translatable(tooltip).getString();
        }
        return setting.getToolTipText() == null ? null : setting.getToolTipText().getString();
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width)
    {
        return y;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final List<ISettingKey<? extends ISetting>> keys = module.getSettingsToShow();
        if (keys.isEmpty())
        {
            graphics.drawString(font, "Ce bâtiment n'a rien à régler.", x + 2, y, Palette.MUTED, false);
            y += 13;
        }
        for (final ISettingKey<? extends ISetting> key : keys)
        {
            final ISetting<?> setting = module.getSetting(key);
            if (setting != null)
            {
                y = row(graphics, mouseX, mouseY, x, y, width, key, setting);
            }
        }
        return y;
    }

    /**
     * One setting's card; returns the y below it.
     */
    private int row(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width,
      final ISettingKey<?> key, final ISetting<?> setting)
    {
        final boolean active = setting.isActive(module);
        final int controlW = controlWidth(setting);
        final int textW = width - controlW - 18;
        final String description = descriptionOf(key, setting);
        final List<net.minecraft.util.FormattedCharSequence> lines = description == null ? List.of() : font.split(Component.literal(description), textW);
        final int shown = Math.min(lines.size(), 3);
        final int height = Math.max(22, 14 + shown * 9 + 3);

        Ui.card(graphics, x, y, width, height, false, false, !active);
        graphics.drawString(font, Component.literal(fit(nameOf(key), textW)).withStyle(ChatFormatting.BOLD), x + 6, y + 4, active ? Palette.INK : Palette.MUTED, false);
        for (int i = 0; i < shown; i++)
        {
            graphics.drawString(font, lines.get(i), x + 6, y + 14 + i * 9, Palette.MUTED, false);
        }
        if (lines.size() > 3 && Ui.inside(mouseX, mouseY, x, y, textW, height))
        {
            hint = description;
        }

        final int cx = x + width - 6 - controlW;
        final int cy = y + (height - 14) / 2;
        if (setting instanceof BoolSetting bool)
        {
            toggle(graphics, mouseX, mouseY, cx, cy, key, bool, active);
        }
        else if (setting instanceof IntSetting number)
        {
            stepper(graphics, mouseX, mouseY, cx, cy, key, number, active);
        }
        else if (setting instanceof StringSetting choice)
        {
            choices(graphics, mouseX, mouseY, cx, cy, key, choice, active);
        }
        if (!active && Ui.inside(mouseX, mouseY, cx, cy, controlW, 14))
        {
            hint = setting.getInactiveReason() != null ? setting.getInactiveReason().getString() : "Pas disponible pour l'instant.";
        }
        return y + height + 3;
    }

    // --- Controls ---

    private int controlWidth(final ISetting<?> setting)
    {
        if (setting instanceof BoolSetting)
        {
            return 62;
        }
        if (setting instanceof IntSetting)
        {
            return 70;
        }
        if (setting instanceof StringSetting choice)
        {
            return segmented(choice) ? segmentedWidth(choice) : Math.min(150, widestOption(choice) + 24);
        }
        return 0;
    }

    private static String option(final String key)
    {
        return Component.translatable(key).getString();
    }

    private int widestOption(final StringSetting choice)
    {
        int widest = 0;
        for (final String option : choice.getSettings())
        {
            widest = Math.max(widest, font.width(option(option)));
        }
        return widest;
    }

    private int segmentedWidth(final StringSetting choice)
    {
        int total = 0;
        for (final String option : choice.getSettings())
        {
            total += font.width(option(option)) + 10;
        }
        return total;
    }

    /**
     * Few short choices are laid out side by side; many or long ones cycle in one button.
     */
    private boolean segmented(final StringSetting choice)
    {
        return choice.getSettings().size() <= 4 && segmentedWidth(choice) <= 190;
    }

    private void toggle(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final ISettingKey<?> key,
      final BoolSetting bool, final boolean active)
    {
        final boolean on = bool.getValue();
        final int half = 31;
        for (int i = 0; i < 2; i++)
        {
            final boolean yes = i == 0;
            final boolean current = yes == on;
            final int bx = x + i * half;
            final boolean over = Ui.inside(mouseX, mouseY, bx, y, half, 14);
            if (current)
            {
                Ui.plate(graphics, bx, y, bx + half, y + 14, yes ? Palette.GREEN : Palette.PAPER_DARK, Palette.EDGE, yes ? Palette.GREEN_LIGHT : Palette.PAPER_DARK);
            }
            else
            {
                Ui.plate(graphics, bx, y, bx + half, y + 14, over && active ? Palette.CARD_HOVER : Palette.PAPER_LIGHT, Palette.RULE, Palette.PAPER_LIGHT);
            }
            final String label = yes ? "Oui" : "Non";
            graphics.drawString(font, label, bx + (half - font.width(label)) / 2, y + 3,
              current ? (yes ? Palette.GREEN_TEXT : Palette.INK) : Palette.MUTED, false);
            if (active && !current)
            {
                addHit(bx, y, half, 14, () -> {
                    bool.trigger();
                    send(key, bool);
                });
            }
        }
    }

    private void stepper(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final ISettingKey<?> key,
      final IntSetting number, final boolean active)
    {
        final int value = number.getValue();
        final boolean overMinus = Ui.inside(mouseX, mouseY, x, y, 16, 14);
        final boolean overPlus = Ui.inside(mouseX, mouseY, x + 54, y, 16, 14);
        Ui.button(graphics, font, x, y, 16, 14, "−", Ui.ButtonStyle.SECONDARY, active && value > 0, overMinus);
        Ui.plate(graphics, x + 18, y, x + 52, y + 14, Palette.PAPER_LIGHT, Palette.RULE, Palette.PAPER_LIGHT);
        final String text = String.valueOf(value);
        graphics.drawString(font, Component.literal(text).withStyle(ChatFormatting.BOLD), x + 35 - font.width(text) / 2, y + 3, Palette.INK, false);
        Ui.button(graphics, font, x + 54, y, 16, 14, "+", Ui.ButtonStyle.SECONDARY, active, overPlus);
        if (active)
        {
            if (value > 0)
            {
                addHit(x, y, 16, 14, () -> change(key, number, -step()));
            }
            addHit(x + 54, y, 16, 14, () -> change(key, number, step()));
            if (overMinus || overPlus)
            {
                hint = "Maj : par 10";
            }
        }
    }

    private static int step()
    {
        return Screen.hasShiftDown() ? 10 : 1;
    }

    private void change(final ISettingKey<?> key, final IntSetting number, final int delta)
    {
        number.setValue(Math.max(0, number.getValue() + delta));
        send(key, number);
    }

    private void choices(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final ISettingKey<?> key,
      final StringSetting choice, final boolean active)
    {
        final List<String> options = choice.getSettings();
        if (segmented(choice))
        {
            int bx = x;
            for (int i = 0; i < options.size(); i++)
            {
                final String label = option(options.get(i));
                final int bw = font.width(label) + 10;
                final boolean current = i == choice.getCurrentIndex();
                final boolean allowed = choice.isIndexAllowed(i);
                final boolean over = Ui.inside(mouseX, mouseY, bx, y, bw, 14);
                if (current)
                {
                    Ui.plate(graphics, bx, y, bx + bw, y + 14, Palette.GREEN, Palette.EDGE, Palette.GREEN_LIGHT);
                }
                else
                {
                    Ui.plate(graphics, bx, y, bx + bw, y + 14, over && active && allowed ? Palette.CARD_HOVER : Palette.PAPER_LIGHT, Palette.RULE, Palette.PAPER_LIGHT);
                }
                graphics.drawString(font, label, bx + 5, y + 3, current ? Palette.GREEN_TEXT : allowed ? Palette.INK : Palette.MUTED, false);
                if (active && allowed && !current)
                {
                    final String value = options.get(i);
                    addHit(bx, y, bw, 14, () -> {
                        choice.set(value);
                        send(key, choice);
                    });
                }
                bx += bw - 1;
            }
            return;
        }
        // Many choices: one button showing the current one, each click the next
        final int bw = controlWidth(choice);
        final boolean over = Ui.inside(mouseX, mouseY, x, y, bw, 14);
        Ui.button(graphics, font, x, y, bw, 14, fit(option(choice.getValue()) + " ⟳", bw - 6), Ui.ButtonStyle.SECONDARY, active, over);
        if (active)
        {
            addHit(x, y, bw, 14, () -> {
                choice.trigger();
                send(key, choice);
            });
            if (over)
            {
                hint = "Clique pour passer au choix suivant (" + (choice.getCurrentIndex() + 1) + " sur " + options.size() + ").";
            }
        }
    }

    /**
     * The new value to the server, the way MineColonies' own page sends it.
     */
    private void send(final ISettingKey<?> key, final ISetting<?> setting)
    {
        new TriggerSettingMessage(building, key, setting, module.getProducer().getRuntimeID()).sendToServer();
    }
}
