package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.buildings.modules.IMinimumStockModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.network.messages.server.colony.building.AddMinimumStockToBuildingModuleMessage;
import com.minecolonies.core.network.messages.server.colony.building.RemoveMinimumStockFromBuildingModuleMessage;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.minecolonies.api.util.Tuple;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What a building always keeps in stock: each item with its quantity in stacks (− and + to change it) and a remove
 * button; adding one opens our item picker. The building orders from the warehouse whatever falls short.
 * Replaces MineColonies' minimum stock page.
 */
public final class MinimumStockScreen extends ModuleScreen
{
    private static final int ROW_H = 24;

    private final int moduleId;
    /**
     * The module of the newest building view (the colony swaps views on each update), this one as a fallback.
     */
    private IMinimumStockModuleView module;

    private MinimumStockScreen(final IBuildingView building, final IMinimumStockModuleView module, @Nullable final Screen parent)
    {
        super("Stock minimum", building, parent, 380);
        this.module = module;
        this.moduleId = module.getProducer().getRuntimeID();
    }

    @Override
    public void tick()
    {
        super.tick();
        final IMinimumStockModuleView fresh = building.getModuleViewMatching(IMinimumStockModuleView.class, m -> m.getProducer().getRuntimeID() == moduleId);
        if (fresh != null)
        {
            module = fresh;
        }
    }

    public static void open(final IBuildingView building, final IMinimumStockModuleView module, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new MinimumStockScreen(building, module, parent));
    }

    private static int step()
    {
        return Screen.hasShiftDown() ? 10 : 1;
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final int count = module.getStock().size();
        final String add = "+ Ajouter un objet";
        final int addW = buttonWidth(add);
        y += Ui.banner(graphics, font, x, y, width - addW - 4, count == 0 ? Ui.State.WAIT : Ui.State.OK,
          count == 0 ? "Rien à garder" : count + (count > 1 ? " objets suivis" : " objet suivi"), "ce qui manque est commandé à l'entrepôt");
        final boolean full = module.hasReachedLimit();
        button(graphics, mouseX, mouseY, x + width - addW, y, add, Ui.ButtonStyle.PRIMARY, !full, full ? "Limite atteinte : améliore le bâtiment pour suivre plus d'objets." : null,
          () -> ItemPickerScreen.open("Garder en stock", building, this, "piles",
            (stack, quantity) -> new AddMinimumStockToBuildingModuleMessage(building, stack, quantity).sendToServer()));
        return y + 20;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final List<Tuple<ItemStorage, Integer>> stock = module.getStock();
        if (stock.isEmpty())
        {
            return Ui.wrapped(graphics, font, Component.literal("Ajoute un objet pour que le bâtiment en ait toujours sous la main."), x + 2, y + 2, width - (2), Palette.MUTED, 10) + 4;
        }
        for (int i = 0; i < stock.size(); i++)
        {
            final Tuple<ItemStorage, Integer> entry = stock.get(i);
            final ItemStack stack = entry.getA().getItemStack().copy();
            final int stacks = entry.getB();
            Ui.card(graphics, x, y, width, ROW_H - 2, false, false, false);
            Icon.of(stack).draw(graphics, x + 3, y + 3, 16);
            addSlot(x + 3, y + 3, 16, stack, mouseY);

            int bx = x + width - 4;
            final String remove = "Retirer";
            bx -= buttonWidth(remove);
            final int index = i;
            button(graphics, mouseX, mouseY, bx, y + 4, remove, Ui.ButtonStyle.DANGER, true, "Ne plus garder cet objet en stock.", () -> {
                module.getStock().remove(index);
                new RemoveMinimumStockFromBuildingModuleMessage(building, stack, module.getProducer().getRuntimeID()).sendToServer();
            });
            // − stacks +
            bx -= 6 + 16;
            button(graphics, mouseX, mouseY, bx, y + 4, "+", Ui.ButtonStyle.SECONDARY, true, "Maj : par 10", () -> change(index, stack, stacks + step()));
            bx -= 36;
            Ui.plate(graphics, bx, y + 4, bx + 34, y + 18, Palette.PAPER_LIGHT, Palette.RULE, Palette.PAPER_LIGHT);
            final String text = String.valueOf(stacks);
            graphics.drawString(font, Component.literal(text).withStyle(ChatFormatting.BOLD), bx + 17 - font.width(text) / 2, y + 7, Palette.INK, false);
            bx -= 18;
            button(graphics, mouseX, mouseY, bx, y + 4, "−", Ui.ButtonStyle.SECONDARY, stacks > 1, "Maj : par 10", () -> change(index, stack, Math.max(1, stacks - step())));

            final int textW = bx - x - 30;
            graphics.drawString(font, Component.literal(fit(stack.getHoverName().getString(), textW)).withStyle(ChatFormatting.BOLD), x + 24, y + 3, Palette.INK, false);
            final int items = stacks * stack.getMaxStackSize();
            graphics.drawString(font, fit(stacks + (stacks > 1 ? " piles" : " pile") + ", soit " + items + " objets", textW), x + 24, y + 12, Palette.MUTED, false);
            y += ROW_H;
        }
        return y;
    }

    private void change(final int index, final ItemStack stack, final int stacks)
    {
        // The module keeps the new quantity for an item it already tracks
        module.getStock().set(index, new Tuple<>(module.getStock().get(index).getA(), stacks));
        new AddMinimumStockToBuildingModuleMessage(building, stack, stacks).sendToServer();
    }
}
