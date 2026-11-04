package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.core.colony.buildings.moduleviews.CraftingModuleView;
import com.minecolonies.core.network.messages.server.colony.building.worker.AddRemoveRecipeMessage;
import com.minecolonies.core.network.messages.server.colony.building.worker.ChangeRecipePriorityMessage;
import com.minecolonies.core.network.messages.server.colony.building.worker.ToggleRecipeMessage;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * A crafter's recipes, in the order the worker tries them: what comes out ← what goes in, the tool or block it needs,
 * then move up / down (Shift: to the top / bottom), switch off or on, and forget. Learning a new one opens
 * MineColonies' crafting grid. Replaces MineColonies' recipe list.
 */
public final class RecipesScreen extends ModuleScreen
{
    private static final int ROW_H = 26;

    private final String moduleId;
    private CraftingModuleView module;

    private RecipesScreen(final IBuildingView building, final CraftingModuleView module, @Nullable final Screen parent)
    {
        super(Component.translatable(module.getDesc().toLowerCase(Locale.US)).getString(), building, parent, 420);
        this.module = module;
        this.moduleId = module.getId();
    }

    public static void open(final IBuildingView building, final CraftingModuleView module, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new RecipesScreen(building, module, parent));
    }

    @Override
    public void tick()
    {
        super.tick();
        final CraftingModuleView fresh = building.getModuleViewMatching(CraftingModuleView.class, m -> m.getId().equals(moduleId));
        if (fresh != null)
        {
            module = fresh;
        }
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width)
    {
        final int count = module.getRecipes().size();
        final int max = module.getMaxRecipes();
        final boolean canLearn = module.isRecipeAlterationAllowed();
        final String learn = "+ Apprendre une recette";
        final int learnW = canLearn ? buttonWidth(learn) + 4 : 0;
        Ui.banner(graphics, font, x, y, width - learnW, count == 0 ? Ui.State.WAIT : Ui.State.OK,
          count + (canLearn ? " / " + max : "") + (count > 1 ? " recettes" : " recette"), "essayées de haut en bas");
        if (canLearn)
        {
            final boolean full = count >= max;
            button(graphics, mouseX, mouseY, x + width - buttonWidth(learn), y, learn, Ui.ButtonStyle.PRIMARY, !full,
              full ? "Plus de place : oublie une recette ou améliore le bâtiment." : "Ouvre la grille d'artisanat pour montrer une recette à l'ouvrier.", module::openCraftingGUI);
        }
        return y + 20;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final List<IRecipeStorage> recipes = module.getRecipes();
        if (recipes.isEmpty())
        {
            graphics.drawString(font, module.isRecipeAlterationAllowed() ? "Aucune recette : apprends-en une à l'ouvrier." : "Aucune recette pour l'instant.",
              x + 2, y + 2, Palette.MUTED, false);
            return y + 14;
        }
        final boolean shift = Screen.hasShiftDown();
        for (int i = 0; i < recipes.size(); i++)
        {
            final IRecipeStorage recipe = recipes.get(i);
            final boolean off = module.isDisabled(recipe);
            Ui.card(graphics, x, y, width, ROW_H - 2, false, false, off);

            // Order number, then output ← inputs
            final String rank = (i + 1) + ".";
            graphics.drawString(font, rank, x + 4, y + 8, Palette.MUTED, false);
            int ix = x + 6 + font.width("99.");
            final List<ItemStack> outputs = recipe.getRecipeType().getOutputDisplayStacks();
            final ItemStack output = outputs.isEmpty() ? recipe.getPrimaryOutput() : outputs.get((int) (Util.getMillis() / 1500 % outputs.size()));
            Icon.of(output).draw(graphics, ix, y + 4, 16);
            addSlot(ix, y + 4, 16, output, mouseY);
            ix += 19;
            graphics.drawString(font, "←", ix, y + 8, Palette.MUTED, false);
            ix += font.width("←") + 3;
            for (final ItemStorage input : recipe.getInput())
            {
                if (ix > x + width - 210)
                {
                    graphics.drawString(font, "…", ix, y + 8, Palette.MUTED, false);
                    break;
                }
                final ItemStack stack = input.getItemStack().copy();
                stack.setCount(input.getAmount());
                Icon.of(stack).draw(graphics, ix, y + 4, 16);
                graphics.renderItemDecorations(font, stack, ix, y + 4);
                addSlot(ix, y + 4, 16, stack, mouseY);
                ix += 18;
            }
            // What else the worker needs
            final String needs = recipe.getRequiredTool() != ModEquipmentTypes.none.get() ? recipe.getRequiredTool().getDisplayName().getString()
                                   : recipe.getIntermediate() != Blocks.AIR ? recipe.getIntermediate().getName().getString() : null;

            // Buttons from the right
            int bx = x + width - 4;
            final int index = i;
            if (module.isRecipeAlterationAllowed())
            {
                bx -= buttonWidth("Oublier");
                button(graphics, mouseX, mouseY, bx, y + 5, "Oublier", Ui.ButtonStyle.DANGER, true, "L'ouvrier oublie cette recette.",
                  () -> new AddRemoveRecipeMessage(building, true, recipe, module.getProducer().getRuntimeID()).sendToServer());
                bx -= 3;
            }
            final String toggle = off ? "Activer" : "Désactiver";
            bx -= buttonWidth(toggle);
            button(graphics, mouseX, mouseY, bx, y + 5, toggle, off ? Ui.ButtonStyle.PRIMARY : Ui.ButtonStyle.SECONDARY, true,
              off ? "L'ouvrier pourra de nouveau la fabriquer." : "L'ouvrier garde la recette mais ne la fabrique plus.", () -> {
                  module.toggle(index);
                  new ToggleRecipeMessage(building, index, module.getProducer().getRuntimeID()).sendToServer();
              });
            bx -= 3 + buttonWidth("▼");
            button(graphics, mouseX, mouseY, bx, y + 5, "▼", Ui.ButtonStyle.SECONDARY, i < recipes.size() - 1, shift ? "Tout en bas" : "Descendre (Maj : tout en bas)", () -> {
                module.switchOrder(index, index + 1, shift);
                new ChangeRecipePriorityMessage(building, index, false, module.getProducer().getRuntimeID(), shift).sendToServer();
            });
            bx -= 2 + buttonWidth("▲");
            button(graphics, mouseX, mouseY, bx, y + 5, "▲", Ui.ButtonStyle.SECONDARY, i > 0, shift ? "Tout en haut" : "Monter (Maj : tout en haut)", () -> {
                module.switchOrder(index, index - 1, shift);
                new ChangeRecipePriorityMessage(building, index, true, module.getProducer().getRuntimeID(), shift).sendToServer();
            });
            if (needs != null && ix + 8 < bx - 4)
            {
                final String text = fit(needs, bx - ix - 12);
                Ui.chip(graphics, font, bx - 6 - font.width(text) - 8, y + 7, text, Palette.PAPER_DARK, Palette.INK, false, false);
            }
            y += ROW_H;
        }
        return y;
    }
}
