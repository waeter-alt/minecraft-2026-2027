package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * Choose an item and a quantity: a searchable grid of every item, what the player carries first; the chosen item,
 * its quantity and the confirm button on top. Replaces MineColonies' resource picker.
 */
public final class ItemPickerScreen extends ModuleScreen
{
    private static final int CELL = 20;

    private final String unit;
    private final BiConsumer<ItemStack, Integer> picked;
    private final List<ItemStack> all = new ArrayList<>();
    @Nullable
    private ItemStack chosen;
    private int quantity = 1;

    /**
     * unit names the quantity (« piles »…).
     */
    private ItemPickerScreen(final String title, final IBuildingView building, final Screen parent, final String unit, final BiConsumer<ItemStack, Integer> picked)
    {
        super(title, building, parent, 400);
        this.unit = unit;
        this.picked = picked;
        // What the player carries first: the likeliest choice
        final List<Item> seen = new ArrayList<>();
        for (final ItemStack stack : Minecraft.getInstance().player.getInventory().items)
        {
            if (!stack.isEmpty() && !seen.contains(stack.getItem()))
            {
                seen.add(stack.getItem());
                all.add(new ItemStack(stack.getItem()));
            }
        }
        for (final Item item : BuiltInRegistries.ITEM)
        {
            if (item != Items.AIR && !seen.contains(item))
            {
                all.add(new ItemStack(item));
            }
        }
    }

    public static void open(final String title, final IBuildingView building, final Screen parent, final String unit, final BiConsumer<ItemStack, Integer> picked)
    {
        Minecraft.getInstance().setScreen(new ItemPickerScreen(title, building, parent, unit, picked));
    }

    @Override
    protected void init()
    {
        super.init();
        addSearch(30);
    }

    @Override
    public void onClose()
    {
        minecraft.setScreen(parent);
    }

    private List<ItemStack> visible()
    {
        final String query = query();
        if (query.isEmpty())
        {
            return all;
        }
        return all.stream().filter(stack -> stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        // The choice, its quantity and the confirm button
        Ui.card(graphics, x, y, width, 22, chosen != null, false, false);
        if (chosen == null)
        {
            graphics.drawString(font, "Choisis un objet ci-dessous.", x + 6, y + 7, Palette.MUTED, false);
        }
        else
        {
            Icon.of(chosen).draw(graphics, x + 3, y + 3, 16);
            addSlot(x + 3, y + 3, 16, chosen, mouseY);
            graphics.drawString(font, Component.literal(fit(chosen.getHoverName().getString(), width - 220)).withStyle(ChatFormatting.BOLD), x + 24, y + 7, Palette.INK, false);
        }
        final String confirm = "Ajouter";
        final int confirmX = x + width - 4 - buttonWidth(confirm);
        button(graphics, mouseX, mouseY, confirmX, y + 4, confirm, Ui.ButtonStyle.PRIMARY, chosen != null, chosen == null ? "Choisis d'abord un objet." : null, () -> {
            picked.accept(chosen.copy(), quantity);
            minecraft.setScreen(parent);
        });
        // − quantity +
        final int sx = confirmX - 8 - 70 - font.width(unit) - 4;
        button(graphics, mouseX, mouseY, sx, y + 4, "−", Ui.ButtonStyle.SECONDARY, quantity > 1, "Maj : par 10", () -> quantity = Math.max(1, quantity - step()));
        Ui.plate(graphics, sx + 18, y + 4, sx + 52, y + 18, Palette.PAPER_LIGHT, Palette.RULE, Palette.PAPER_LIGHT);
        final String text = String.valueOf(quantity);
        graphics.drawString(font, Component.literal(text).withStyle(ChatFormatting.BOLD), sx + 35 - font.width(text) / 2, y + 7, Palette.INK, false);
        button(graphics, mouseX, mouseY, sx + 54, y + 4, "+", Ui.ButtonStyle.SECONDARY, true, "Maj : par 10", () -> quantity += step());
        graphics.drawString(font, unit, sx + 74, y + 7, Palette.MUTED, false);
        y += 26;
        Ui.label(graphics, font, "TOUS LES OBJETS · TON INVENTAIRE D'ABORD", x, y + 3);
        return y + 18;
    }

    private static int step()
    {
        return Screen.hasShiftDown() ? 10 : 1;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width)
    {
        final List<ItemStack> visible = visible();
        final int columns = Math.max(1, width / CELL);
        final int rows = (visible.size() + columns - 1) / columns;
        // Only the rows on screen are drawn: there are thousands of items
        final int top = y0 + HEADER;
        final int bottom = y0 + h;
        for (int row = 0; row < rows; row++)
        {
            final int cy = y + row * CELL;
            if (cy + CELL < top || cy > bottom)
            {
                continue;
            }
            for (int column = 0; column < columns; column++)
            {
                final int index = row * columns + column;
                if (index >= visible.size())
                {
                    break;
                }
                final ItemStack stack = visible.get(index);
                final int cx = x + column * CELL;
                final boolean over = Ui.inside(mouseX, mouseY, cx, cy, CELL - 1, CELL - 1);
                final boolean selected = chosen != null && ItemStack.isSameItem(chosen, stack);
                if (selected || over)
                {
                    Ui.plate(graphics, cx, cy, cx + CELL - 1, cy + CELL - 1, selected ? Palette.GREEN_SOFT : Palette.CARD_HOVER, selected ? Palette.GREEN : Palette.RULE,
                      selected ? Palette.GREEN_SOFT : Palette.CARD_HOVER);
                }
                Icon.of(stack).draw(graphics, cx + 2, cy + 2, 16);
                addSlot(cx + 2, cy + 2, 16, stack, mouseY);
                addHit(cx, cy, CELL - 1, CELL - 1, () -> chosen = stack);
            }
        }
        if (visible.isEmpty())
        {
            graphics.drawString(font, "Aucun objet ne correspond à « " + query() + " ».", x + 2, y + 2, Palette.MUTED, false);
            return y + 14;
        }
        return y + rows * CELL;
    }
}
