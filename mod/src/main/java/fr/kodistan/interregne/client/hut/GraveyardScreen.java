package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.colony.buildings.moduleviews.GraveyardManagementModuleView;
import com.minecolonies.core.tileentities.TileEntityGrave;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The graveyard: graves waiting for the undertaker, with whose they are and how far, then the citizens laid to rest
 * here. Replaces MineColonies' graveyard page.
 */
public final class GraveyardScreen extends ModuleScreen
{
    private final int moduleId;
    private GraveyardManagementModuleView module;

    private GraveyardScreen(final IBuildingView building, final GraveyardManagementModuleView module, @Nullable final Screen parent)
    {
        super("Cimetière", building, parent, 340);
        this.module = module;
        this.moduleId = module.getProducer().getRuntimeID();
    }

    public static void open(final IBuildingView building, final GraveyardManagementModuleView module, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new GraveyardScreen(building, module, parent));
    }

    @Override
    public void tick()
    {
        super.tick();
        final GraveyardManagementModuleView fresh = building.getModuleViewMatching(GraveyardManagementModuleView.class, m -> m.getProducer().getRuntimeID() == moduleId);
        if (fresh != null)
        {
            module = fresh;
        }
        module.cleanGraves();
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width)
    {
        final int graves = module.getGraves().size();
        Ui.banner(graphics, font, x, y, width, graves == 0 ? Ui.State.OK : Ui.State.WAIT, graves == 0 ? "Aucune tombe" : graves + (graves > 1 ? " tombes" : " tombe"),
          graves == 0 ? "personne n'attend le fossoyeur" : "le fossoyeur va s'en occuper");
        return y + 19;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        Ui.label(graphics, font, "TOMBES À RELEVER", x, y);
        y += 11;
        if (module.getGraves().isEmpty())
        {
            graphics.drawString(font, "Aucune.", x + 2, y, Palette.MUTED, false);
            y += 13;
        }
        for (final BlockPos grave : module.getGraves())
        {
            Ui.card(graphics, x, y, width, 20, false, false, false);
            Icon.of(Items.SKELETON_SKULL).draw(graphics, x + 3, y + 2, 16);
            String name = "un citoyen inconnu";
            if (Minecraft.getInstance().level.getBlockEntity(grave) instanceof TileEntityGrave tile && tile.getGraveData() != null)
            {
                name = tile.getGraveData().getCitizenName();
            }
            final int distance = (int) Math.sqrt(grave.distSqr(building.getPosition()));
            graphics.drawString(font, Component.literal("Tombe de " + name).withStyle(ChatFormatting.BOLD), x + 23, y + 6, Palette.INK, false);
            final String where = "à " + distance + " blocs";
            graphics.drawString(font, where, x + width - 6 - font.width(where), y + 6, Palette.MUTED, false);
            y += 22;
        }
        y = Ui.separator(graphics, x, y, width);
        final List<String> resting = module.getRestingCitizen();
        Ui.label(graphics, font, "REPOSENT ICI", x, y);
        graphics.drawString(font, String.valueOf(resting.size()), x + font.width("REPOSENT ICI") + 6, y, Palette.INK, false);
        y += 12;
        if (resting.isEmpty())
        {
            return Ui.wrapped(graphics, font, Component.literal("Personne pour l'instant."), x + 2, y, width - (2), Palette.MUTED, 10) + 3;
        }
        for (final String citizen : resting)
        {
            graphics.drawString(font, fit("✝ " + citizen, width - (4)), x + 4, y, Palette.INK, false);
            y += 11;
        }
        return y;
    }
}
