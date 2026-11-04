package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.colony.buildings.moduleviews.WarehouseOptionsModuleView;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingWareHouse;
import com.minecolonies.core.network.messages.server.colony.building.MarkBuildingDirtyMessage;
import com.minecolonies.core.network.messages.server.colony.building.warehouse.SortWarehouseMessage;
import com.minecolonies.core.network.messages.server.colony.building.warehouse.UpgradeWarehouseMessage;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The warehouse's two tools: tidy the racks, and, once the warehouse is at its top level, more room for an emerald
 * block each time. Replaces MineColonies' warehouse options page.
 */
public final class WarehouseOptionsScreen extends ModuleScreen
{
    private static final int SORT_LEVEL = 3;

    private final int moduleId;
    private WarehouseOptionsModuleView module;
    private boolean sorted;

    private WarehouseOptionsScreen(final IBuildingView building, final WarehouseOptionsModuleView module, @Nullable final Screen parent)
    {
        super("Entrepôt", building, parent, 340);
        this.module = module;
        this.moduleId = module.getProducer().getRuntimeID();
        new MarkBuildingDirtyMessage(building).sendToServer();
    }

    public static void open(final IBuildingView building, final WarehouseOptionsModuleView module, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new WarehouseOptionsScreen(building, module, parent));
    }

    @Override
    public void tick()
    {
        super.tick();
        final WarehouseOptionsModuleView fresh = building.getModuleViewMatching(WarehouseOptionsModuleView.class, m -> m.getProducer().getRuntimeID() == moduleId);
        if (fresh != null)
        {
            module = fresh;
        }
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width)
    {
        return y;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        // Tidy up
        Ui.card(graphics, x, y, width, 34, false, false, false);
        Icon.of(Items.CHEST).draw(graphics, x + 5, y + 9, 16);
        graphics.drawString(font, Component.literal("Ranger l'entrepôt").withStyle(ChatFormatting.BOLD), x + 26, y + 6, Palette.INK, false);
        graphics.drawString(font, fit("regroupe les objets semblables dans les mêmes coffres", width - (26)), x + 26, y + 18, Palette.MUTED, false);
        final boolean canSort = building.getBuildingLevel() >= SORT_LEVEL;
        final String sort = sorted ? "Rangé ✔" : "Ranger";
        button(graphics, mouseX, mouseY, x + width - 6 - buttonWidth(sort), y + 10, sort, Ui.ButtonStyle.PRIMARY, canSort && !sorted,
          canSort ? null : "Possible à partir du niveau " + SORT_LEVEL + " de l'entrepôt.", () -> {
              new SortWarehouseMessage(building).sendToServer();
              sorted = true;
          });
        y += 38;

        // More room
        final int level = module.getStorageUpgradeLevel();
        final int max = BuildingWareHouse.MAX_STORAGE_UPGRADE;
        Ui.card(graphics, x, y, width, 34, false, false, false);
        Icon.of(Items.EMERALD_BLOCK).draw(graphics, x + 5, y + 9, 16);
        graphics.drawString(font, Component.literal("Agrandir le stockage").withStyle(ChatFormatting.BOLD), x + 26, y + 6, Palette.INK, false);
        graphics.drawString(font, level + " / " + max + " agrandissements · 1 bloc d'émeraude chacun", x + 26, y + 18, Palette.MUTED, false);
        final boolean topLevel = building.getBuildingLevel() >= building.getBuildingMaxLevel();
        final boolean room = level < max;
        final boolean hasEmerald = minecraft.player.isCreative() || minecraft.player.getInventory().countItem(Items.EMERALD_BLOCK) > 0;
        final String why = !topLevel ? "Possible quand l'entrepôt est au niveau " + building.getBuildingMaxLevel() + "."
                             : !room ? "Le stockage est déjà au maximum."
                                 : !hasEmerald ? "Il te faut un bloc d'émeraude dans ton inventaire." : "Prend un bloc d'émeraude de ton inventaire.";
        final String upgrade = "Agrandir";
        button(graphics, mouseX, mouseY, x + width - 6 - buttonWidth(upgrade), y + 10, upgrade, Ui.ButtonStyle.PRIMARY, topLevel && room && hasEmerald, why, () -> {
            new UpgradeWarehouseMessage(building).sendToServer();
            module.incrementStorageUpgrade();
        });
        return y + 38;
    }
}
