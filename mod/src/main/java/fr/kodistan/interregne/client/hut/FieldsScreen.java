package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.colony.buildingextensions.PlantationField;
import com.minecolonies.core.colony.buildings.moduleviews.FieldsModuleView;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingPlantation;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The fields a farmer or a plantation works: each field with what grows there, where it is, and a button to take it
 * or let it go, in manual mode; in automatic mode the building picks its fields itself. Replaces MineColonies' field
 * pages.
 */
public final class FieldsScreen extends ModuleScreen
{
    private static final int ROW_H = 24;

    private final int moduleId;
    private FieldsModuleView module;

    private FieldsScreen(final IBuildingView building, final FieldsModuleView module, @Nullable final Screen parent)
    {
        super(module instanceof BuildingPlantation.PlantationFieldsModuleView ? "Parcelles" : "Champs", building, parent, 380);
        this.module = module;
        this.moduleId = module.getProducer().getRuntimeID();
    }

    public static void open(final IBuildingView building, final FieldsModuleView module, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new FieldsScreen(building, module, parent));
    }

    @Override
    public void tick()
    {
        super.tick();
        final FieldsModuleView fresh = building.getModuleViewMatching(FieldsModuleView.class, m -> m.getProducer().getRuntimeID() == moduleId);
        if (fresh != null)
        {
            module = fresh;
        }
    }

    private static String direction(final BlockPosUtil.DirectionResult direction)
    {
        return switch (direction)
        {
            case NORTH -> "au nord";
            case SOUTH -> "au sud";
            case WEST -> "à l'ouest";
            case EAST -> "à l'est";
            case NORTH_WEST -> "au nord-ouest";
            case NORTH_EAST -> "au nord-est";
            case SOUTH_WEST -> "au sud-ouest";
            case SOUTH_EAST -> "au sud-est";
            case UP -> "au-dessus";
            case DOWN -> "en dessous";
            case SAME -> "ici";
        };
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final int owned = module.getOwnedFields().size();
        final int max = module.getMaxFieldCount();
        String detail = owned == 0 ? "le bâtiment n'a rien à cultiver" : "le bâtiment cultive ce qui est coché";
        if (module instanceof BuildingPlantation.PlantationFieldsModuleView plantation)
        {
            detail = plantation.getCurrentPlants() + " plantes sur " + plantation.getMaxConcurrentPlants() + " possibles";
        }
        Ui.banner(graphics, font, x, y, width, owned == 0 ? Ui.State.BAD : Ui.State.OK, owned + " / " + max + (max > 1 ? " parcelles" : " parcelle"), detail);
        y += 19;
        final boolean manual = module.assignFieldManually();
        final int segW = segmented(graphics, mouseX, mouseY, x, y, List.of("Automatique", "Manuelle"), manual ? 1 : 0, index -> module.setAssignFieldManually(index == 1));
        final String note = manual ? "tu choisis les parcelles" : "le bâtiment prend les parcelles libres les plus proches";
        final int noteX = x + segW + 8;
        if (font.width(note) <= x + width - noteX)
        {
            graphics.drawString(font, note, noteX, y + 3, Palette.MUTED, false);
            return y + 20;
        }
        // Too narrow beside the switch: under it
        return Ui.wrapped(graphics, font, Component.literal(note), x + 2, y + 18, width - 4, Palette.MUTED, 10) + 4;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final List<IBuildingExtension> fields = module.getFields();
        if (fields.isEmpty())
        {
            return Ui.wrapped(graphics, font, Component.literal("Aucune parcelle trouvée : pose-en une près du bâtiment."), x + 2, y + 2, width - (2), Palette.MUTED, 10) + 4;
        }
        final boolean manual = module.assignFieldManually();
        for (final IBuildingExtension field : fields)
        {
            final boolean mine = module.getOwnedFields().contains(field);
            Ui.card(graphics, x, y, width, ROW_H - 2, mine, false, !mine && field.isTaken());

            ItemStack crop = new ItemStack(Items.WHEAT_SEEDS);
            String name = "Champ sans graine";
            String extra = null;
            if (field instanceof FarmField farm)
            {
                if (!farm.getSeed().isEmpty())
                {
                    crop = farm.getSeed();
                    name = crop.getHoverName().getString();
                }
                extra = "ensuite : " + farm.getFieldStage().getNextStage().getStageText().getString().toLowerCase(java.util.Locale.FRENCH);
            }
            else if (field instanceof PlantationField plantation)
            {
                crop = new ItemStack(plantation.getModule().getItem());
                name = crop.getHoverName().getString();
            }
            Icon.of(crop).draw(graphics, x + 3, y + 3, 16);
            addSlot(x + 3, y + 3, 16, crop, mouseY);

            // Take or let go
            int bx = x + width - 4;
            final String action;
            final Ui.ButtonStyle style;
            boolean enabled = manual;
            String why = manual ? null : "Passe en manuelle pour choisir les parcelles.";
            if (mine)
            {
                action = "Libérer";
                style = Ui.ButtonStyle.SECONDARY;
            }
            else if (field.isTaken())
            {
                action = "Prise";
                style = Ui.ButtonStyle.SECONDARY;
                enabled = false;
                why = "Un autre bâtiment cultive déjà cette parcelle.";
            }
            else
            {
                action = "Prendre";
                style = Ui.ButtonStyle.PRIMARY;
                if (manual && !module.canAssignField(field))
                {
                    enabled = false;
                    why = module.getFieldWarningTooltip(field) == null ? "Plus de place pour une parcelle." : module.getFieldWarningTooltip(field).getString();
                }
            }
            bx -= buttonWidth(action);
            button(graphics, mouseX, mouseY, bx, y + 4, action, style, enabled, why, () -> {
                if (mine)
                {
                    module.freeField(field);
                }
                else
                {
                    module.assignField(field);
                }
            });

            final int textW = bx - x - 30;
            graphics.drawString(font, Component.literal(fit(name, textW)).withStyle(ChatFormatting.BOLD), x + 23, y + 3, Palette.INK, false);
            // Despite its name, MineColonies returns the distance itself
            final int distance = field.getSqDistance(building);
            final String where = "à " + distance + " blocs " + direction(BlockPosUtil.calcDirection(building.getPosition(), field.getPosition()))
                                   + (extra == null ? "" : " · " + extra);
            graphics.drawString(font, fit(where, textW), x + 23, y + 12, Palette.MUTED, false);
            y += ROW_H;
        }
        return y;
    }
}
