package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.buildings.modules.IItemListModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.items.IMinecoloniesFoodItem;
import com.minecolonies.api.items.ModTags;
import com.minecolonies.api.util.FoodUtils;
import com.minecolonies.core.colony.buildings.moduleviews.FoodItemListModuleView;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * An item filter of a building (what a worker may use, burn, eat, collect…): every item as a card that turns green
 * when on, in two columns, with a search, a filter on what is on or off, and bulk switches for what is shown.
 * Food lists also show the level of house each food needs. Replaces MineColonies' filterable lists.
 */
public final class ItemListScreen extends ModuleScreen
{
    private static final int CARD_H = 20;

    private final String moduleId;
    private final boolean food;
    private final List<ItemStorage> all;
    /**
     * 0 all, 1 on only, 2 off only.
     */
    private static int shown;

    private ItemListScreen(final IBuildingView building, final IItemListModuleView module, @Nullable final Screen parent)
    {
        super(Component.translatable(module.getDesc().toLowerCase(Locale.US)).getString(), building, parent, 420);
        this.moduleId = module.getId();
        this.food = module instanceof FoodItemListModuleView;
        final List<ItemStorage> items = new ArrayList<>(module.getAllItems().apply(building));
        if (food)
        {
            // As MineColonies' food list: only what the house's level lets people eat
            items.removeIf(c -> c.getItemStack().is(ModTags.excludedFood) || !FoodUtils.canEatLevel(c.getItemStack(), building.getBuildingLevel() - 1));
            items.sort(Comparator.comparingInt((ItemStorage c) -> -tier(c.getItemStack())).thenComparing(c -> c.getItemStack().getHoverName().getString()));
        }
        else
        {
            items.sort(Comparator.comparing(c -> c.getItemStack().getHoverName().getString()));
        }
        this.all = items;
    }

    public static void open(final IBuildingView building, final IItemListModuleView module, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new ItemListScreen(building, module, parent));
    }

    @Override
    protected void init()
    {
        super.init();
        addSearch(24);
    }

    @Nullable
    private IItemListModuleView module()
    {
        return building.getModuleViewMatching(IItemListModuleView.class, view -> view.getId().equals(moduleId));
    }

    private static int tier(final ItemStack stack)
    {
        return stack.getItem() instanceof IMinecoloniesFoodItem item ? item.getTier() : 0;
    }

    /**
     * Whether the item is switched on, whichever way the module stores it (a list of allowed items, or of refused ones).
     */
    private static boolean on(final IItemListModuleView module, final ItemStorage item)
    {
        final boolean listed = module.isAllowedItem(item);
        return module.isInverted() != listed;
    }

    private static void set(final IItemListModuleView module, final ItemStorage item, final boolean on)
    {
        if (on(module, item) == on)
        {
            return;
        }
        if (on != module.isInverted())
        {
            module.addItem(item);
        }
        else
        {
            module.removeItem(item);
        }
    }

    private List<ItemStorage> visible(final IItemListModuleView module)
    {
        final String query = query();
        final List<ItemStorage> visible = new ArrayList<>();
        for (final ItemStorage item : all)
        {
            if (!query.isEmpty() && !item.getItemStack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query))
            {
                continue;
            }
            final boolean on = on(module, item);
            if ((shown == 1 && !on) || (shown == 2 && on))
            {
                continue;
            }
            visible.add(item);
        }
        return visible;
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final IItemListModuleView module = module();
        if (module == null)
        {
            return y;
        }
        int count = 0;
        for (final ItemStorage item : all)
        {
            if (on(module, item))
            {
                count++;
            }
        }
        final String title = count + " sur " + all.size() + (count > 1 ? " cochés" : " coché");
        y += Ui.banner(graphics, font, x, y, width, count == 0 ? Ui.State.WAIT : Ui.State.OK, title,
          count == 0 ? "rien n'est autorisé pour l'instant" : "seuls les objets cochés sont pris en compte");
        y += 18;
        // The search box follows the row, which moves down when the banner takes two lines
        if (search != null)
        {
            search.setY(y);
        }

        segmented(graphics, mouseX, mouseY, x, y, List.of("Tous", "Cochés", "Décochés"), shown, index -> {
            shown = index;
            resetScroll();
        });
        y += 18;

        // Bulk switches act on what the filters show
        final List<ItemStorage> visible = visible(module);
        int bx = x;
        bx += button(graphics, mouseX, mouseY, bx, y, "Tout cocher", Ui.ButtonStyle.SECONDARY, !visible.isEmpty(),
          query().isEmpty() ? "Coche tous les objets affichés." : "Coche les objets trouvés par la recherche.", () -> visible.forEach(item -> set(module, item, true))) + 3;
        bx += button(graphics, mouseX, mouseY, bx, y, "Tout décocher", Ui.ButtonStyle.SECONDARY, !visible.isEmpty(),
          query().isEmpty() ? "Décoche tous les objets affichés." : "Décoche les objets trouvés par la recherche.", () -> visible.forEach(item -> set(module, item, false))) + 3;
        final String reset = "Par défaut";
        button(graphics, mouseX, mouseY, x + width - buttonWidth(reset), y, reset, Ui.ButtonStyle.SECONDARY, true,
          "Remet la liste comme au départ : " + (module.isInverted() ? "tout coché." : "rien de coché."), module::clearItems);
        if (food)
        {
            final String legend = "Niv. = maison requise";
            graphics.drawString(font, legend, bx + 6, y + 3, Palette.MUTED, false);
        }
        return y + 19;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final IItemListModuleView module = module();
        if (module == null)
        {
            return y;
        }
        final List<ItemStorage> visible = visible(module);
        if (visible.isEmpty())
        {
            graphics.drawString(font, query().isEmpty() ? "Aucun objet ici." : "Aucun objet ne correspond à « " + query() + " ».", x + 2, y + 2, Palette.MUTED, false);
            return y + 14;
        }
        final int columnW = (width - 3) / 2;
        for (int i = 0; i < visible.size(); i++)
        {
            final ItemStorage item = visible.get(i);
            final int cx = x + (i % 2) * (columnW + 3);
            final int cy = y + (i / 2) * (CARD_H + 2);
            card(graphics, mouseX, mouseY, cx, cy, columnW, module, item);
        }
        return y + ((visible.size() + 1) / 2) * (CARD_H + 2);
    }

    private void card(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width, final IItemListModuleView module,
      final ItemStorage item)
    {
        final boolean on = on(module, item);
        final boolean over = Ui.inside(mouseX, mouseY, x, y, width, CARD_H);
        final int fill = on ? (over ? Palette.GREEN_LIGHT : Palette.GREEN_SOFT) : (over ? Palette.CARD_HOVER : Palette.PAPER_DARK);
        Ui.plate(graphics, x, y, x + width, y + CARD_H, fill, on ? Palette.GREEN : Palette.RULE, fill);
        final ItemStack stack = item.getItemStack();
        Icon.of(stack).draw(graphics, x + 3, y + 2, 16);
        addSlot(x + 3, y + 2, 16, stack, mouseY);

        int right = x + width - 4;
        final String mark = on ? "✔" : "✕";
        right -= font.width(mark);
        graphics.drawString(font, mark, right, y + 6, on ? Palette.GREEN : Palette.MUTED, false);
        if (food)
        {
            final int level = FoodUtils.getBuildingLevelForFood(stack);
            final String badge = "Niv. " + level;
            right -= font.width(badge) + 12;
            Ui.levelBadge(graphics, font, right, y + 4, badge, level);
        }
        graphics.drawString(font, fit(stack.getHoverName().getString(), right - x - 26), x + 22, y + 6, on ? Palette.INK : Palette.MUTED, false);
        addHit(x, y, width, CARD_H, () -> set(module, item, !on));
    }
}
