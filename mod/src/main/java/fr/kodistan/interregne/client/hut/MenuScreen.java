package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.items.IMinecoloniesFoodItem;
import com.minecolonies.api.util.FoodUtils;
import com.minecolonies.core.colony.buildings.moduleviews.RestaurantMenuModuleView;
import com.minecolonies.core.network.messages.server.colony.building.AlterRestaurantMenuItemMessage;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The restaurant's menu: what is served, as chips to take off in one click, then every dish the restaurant's level
 * allows, best first, each added in one click. Every dish shows the level of house it suits.
 * Replaces MineColonies' menu page.
 */
public final class MenuScreen extends ModuleScreen
{
    private static final int CARD_H = 20;

    private final int moduleId;
    private RestaurantMenuModuleView module;
    private final List<ItemStorage> dishes;

    private MenuScreen(final IBuildingView building, final RestaurantMenuModuleView module, @Nullable final Screen parent)
    {
        super("Menu", building, parent, 420);
        this.module = module;
        this.moduleId = module.getProducer().getRuntimeID();
        final List<ItemStorage> edible = new ArrayList<>(IColonyManager.getInstance().getCompatibilityManager().getEdibles(building.getBuildingLevel() - 1));
        edible.sort(Comparator.comparingInt((ItemStorage s) -> -quality(s.getItemStack())).thenComparing(s -> s.getItemStack().getHoverName().getString()));
        this.dishes = edible;
    }

    public static void open(final IBuildingView building, final RestaurantMenuModuleView module, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new MenuScreen(building, module, parent));
    }

    @Override
    protected void init()
    {
        super.init();
        addSearch(0);
        search.visible = false;
    }

    @Override
    public void tick()
    {
        super.tick();
        final RestaurantMenuModuleView fresh = building.getModuleViewMatching(RestaurantMenuModuleView.class, m -> m.getProducer().getRuntimeID() == moduleId);
        if (fresh != null)
        {
            module = fresh;
        }
    }

    /**
     * Colony dishes by tier first, then plain food by how much it feeds.
     */
    private static int quality(final ItemStack stack)
    {
        if (stack.getItem() instanceof IMinecoloniesFoodItem food)
        {
            return 100 * food.getTier();
        }
        return stack.getFoodProperties(null) == null ? 0 : stack.getFoodProperties(null).nutrition();
    }

    private boolean onMenu(final ItemStorage dish)
    {
        return module.getMenu().contains(dish);
    }

    private void add(final ItemStorage dish)
    {
        module.getMenu().add(dish);
        AlterRestaurantMenuItemMessage.addMenuItem(building, dish.getItemStack(), moduleId).sendToServer();
    }

    private void remove(final ItemStorage dish)
    {
        module.getMenu().remove(dish);
        AlterRestaurantMenuItemMessage.removeMenuItem(building, dish.getItemStack(), moduleId).sendToServer();
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final List<ItemStorage> menu = new ArrayList<>(module.getMenu());
        menu.sort(Comparator.comparingInt((ItemStorage s) -> -quality(s.getItemStack())));
        final boolean fine = menu.stream().anyMatch(s -> s.getItem() instanceof IMinecoloniesFoodItem food && food.getTier() >= 2);
        if (menu.isEmpty())
        {
            Ui.banner(graphics, font, x, y, width, Ui.State.BAD, "Menu vide", "personne ne peut manger ici : ajoute des plats ci-dessous");
        }
        else if (!fine)
        {
            Ui.banner(graphics, font, x, y, width, Ui.State.WAIT, menu.size() + (menu.size() > 1 ? " plats" : " plat"),
              "sans plat de niveau 2 ou plus, les citoyens mangent moins bien");
        }
        else
        {
            Ui.banner(graphics, font, x, y, width, Ui.State.OK, menu.size() + (menu.size() > 1 ? " plats" : " plat"),
              module.hasReachedLimit() ? "le menu est complet" : "on peut encore en ajouter");
        }
        y += 18;

        // What is served, as chips: a click takes the dish off
        Ui.label(graphics, font, "AU MENU", x, y + 3);
        int cx = x + font.width("AU MENU") + 8;
        int cy = y;
        for (final ItemStorage dish : menu)
        {
            final ItemStack stack = dish.getItemStack();
            final String name = fit(stack.getHoverName().getString(), 110);
            final int cw = 16 + font.width(name) + font.width(" ✕") + 10;
            if (cx + cw > x + width)
            {
                cx = x + font.width("AU MENU") + 8;
                cy += 18;
            }
            final boolean over = Ui.inside(mouseX, mouseY, cx, cy, cw, 16);
            Ui.plate(graphics, cx, cy, cx + cw, cy + 16, over ? Palette.RED_SOFT : Palette.GREEN_SOFT, over ? Palette.RED : Palette.GREEN, over ? Palette.RED_SOFT : Palette.GREEN_SOFT);
            Icon.of(stack).draw(graphics, cx + 2, cy + 2, 12);
            graphics.drawString(font, name + " ✕", cx + 17, cy + 4, over ? Palette.RED : Palette.INK, false);
            addHit(cx, cy, cw, 16, () -> remove(dish));
            if (over)
            {
                hint = "Retirer « " + stack.getHoverName().getString() + " » du menu";
            }
            cx += cw + 3;
        }
        if (menu.isEmpty())
        {
            graphics.drawString(font, "rien pour l'instant", cx, y + 4, Palette.MUTED, false);
        }
        y = cy + 22;

        Ui.label(graphics, font, "PLATS POSSIBLES", x, y + 3);
        graphics.drawString(font, "Niv. = maison qu'il contente", x + font.width("PLATS POSSIBLES") + 8, y + 3, Palette.MUTED, false);
        search.setY(y);
        search.visible = true;
        return y + 17;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width)
    {
        final String query = query();
        final List<ItemStorage> shown = dishes.stream()
          .filter(d -> query.isEmpty() || d.getItemStack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query))
          .toList();
        if (shown.isEmpty())
        {
            graphics.drawString(font, "Aucun plat ne correspond à « " + query + " ».", x + 2, y + 2, Palette.MUTED, false);
            return y + 14;
        }
        final boolean full = module.hasReachedLimit();
        final int columnW = (width - 3) / 2;
        for (int i = 0; i < shown.size(); i++)
        {
            final ItemStorage dish = shown.get(i);
            final ItemStack stack = dish.getItemStack();
            final int cx = x + (i % 2) * (columnW + 3);
            final int cy = y + (i / 2) * (CARD_H + 2);
            final boolean served = onMenu(dish);
            final boolean over = Ui.inside(mouseX, mouseY, cx, cy, columnW, CARD_H);
            final int fill = served ? Palette.GREEN_SOFT : over && !full ? Palette.CARD_HOVER : Palette.PAPER_LIGHT;
            Ui.plate(graphics, cx, cy, cx + columnW, cy + CARD_H, fill, served ? Palette.GREEN : Palette.RULE, fill);
            Icon.of(stack).draw(graphics, cx + 3, cy + 2, 16);
            addSlot(cx + 3, cy + 2, 16, stack, mouseY);

            int right = cx + columnW - 4;
            final String mark = served ? "✔" : "+";
            right -= font.width(mark);
            graphics.drawString(font, mark, right, cy + 6, served ? Palette.GREEN : full ? Palette.MUTED : Palette.INK, false);
            final int level = FoodUtils.getBuildingLevelForFood(stack);
            final String badge = "Niv. " + level;
            right -= font.width(badge) + 12;
            Ui.levelBadge(graphics, font, right, cy + 4, badge, level);
            graphics.drawString(font, fit(stack.getHoverName().getString(), right - cx - 26), cx + 22, cy + 6, Palette.INK, false);

            if (served)
            {
                addHit(cx, cy, columnW, CARD_H, () -> remove(dish));
                if (over)
                {
                    hint = "Au menu. Clique pour le retirer.";
                }
            }
            else if (!full)
            {
                addHit(cx, cy, columnW, CARD_H, () -> add(dish));
            }
            else if (over)
            {
                hint = "Le menu est complet : retire un plat ou améliore le restaurant.";
            }
        }
        return y + ((shown.size() + 1) / 2) * (CARD_H + 2);
    }
}
