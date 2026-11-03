package fr.kodistan.interregne.client.catalogue;

import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.structurize.client.gui.WindowExtendedBuildTool;
import com.ldtteam.structurize.client.gui.WindowSwitchPack;
import com.ldtteam.structurize.storage.StructurePacks;
import com.ldtteam.structurize.storage.rendering.RenderingCache;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Glue between the build tool window and the catalogue: open it first, hide the old folder menus,
 * and add a "Catalogue" button to come back to it.
 */
public final class BuildCatalogue
{
    private static final String[] FOLDER_PANES = {"switch", "categories", "subcategories", "blueprints", "alternatives", "levels"};
    private static final String BUTTON_ID = "interregne_catalogue";
    private static final ResourceLocation BUTTON = ResourceLocation.fromNamespaceAndPath("structurize", "textures/gui/buildtool/button_medium.png");

    private static boolean forceCatalogue;

    private BuildCatalogue()
    {
    }

    /**
     * Structurize's style browser, with each style's picture; picking one comes back to the catalogue.
     */
    public static void openStyles(final WindowExtendedBuildTool window)
    {
        new WindowSwitchPack(() -> {
            forceCatalogue = true;
            return window;
        }).open();
    }

    /**
     * Called when the build tool window opens with nothing selected: show the catalogue right after.
     */
    public static void openFor(final WindowExtendedBuildTool window)
    {
        if (StructurePacks.getSelectedPack() == null)
        {
            // First use: Structurize asks for a style first
            return;
        }
        final boolean force = forceCatalogue;
        forceCatalogue = false;
        if (!force && RenderingCache.getOrCreateBlueprintPreviewData("blueprint").getBlueprint() != null)
        {
            // A building waits to be placed: back to moving and rotating it
            return;
        }
        Minecraft.getInstance().tell(() -> {
            if (Minecraft.getInstance().screen instanceof BOScreen screen && screen.getWindow() == window)
            {
                CatalogueScreen.open(window, anchor());
            }
        });
    }

    public static void hideFolders(final WindowExtendedBuildTool window)
    {
        for (final String id : FOLDER_PANES)
        {
            final Pane pane = window.findPaneByID(id);
            if (pane != null && pane.isVisible())
            {
                pane.hide();
            }
        }
        if (window.findPaneByID(BUTTON_ID) == null)
        {
            final ButtonImage button = new ButtonImage();
            button.setID(BUTTON_ID);
            button.setSize(86, 17);
            button.setPosition(5, 5);
            button.setImage(BUTTON);
            button.setText(Component.literal("☰ Catalogue"));
            button.setColors(0x000000);
            button.setHandler(b -> CatalogueScreen.open(window, anchor()));
            window.addChild(button);
        }
    }

    private static BlockPos anchor()
    {
        final BlockPos pos = RenderingCache.getOrCreateBlueprintPreviewData("blueprint").getPos();
        return pos != null ? pos : Minecraft.getInstance().player.blockPosition();
    }
}
