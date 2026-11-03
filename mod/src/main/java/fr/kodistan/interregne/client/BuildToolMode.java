package fr.kodistan.interregne.client;

import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.structurize.client.gui.WindowExtendedBuildTool;
import com.ldtteam.structurize.network.messages.BuildToolPlacementMessage;
import fr.kodistan.interregne.network.PlacementModePayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The build tool's mode toggle: "Construire maintenant" places a hut and starts its construction at once,
 * "Poser le plan" only places the hut block. The choice is remembered for the whole game session.
 */
public final class BuildToolMode
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("structurize", "textures/gui/buildtool/button_medium.png");
    private static final ResourceLocation TEXTURE_OFF = ResourceLocation.fromNamespaceAndPath("structurize", "textures/gui/buildtool/button_medium_disabled.png");

    private static boolean buildNow = true;

    private BuildToolMode()
    {
    }

    /**
     * Add the toggle between the cancel and confirm buttons of the build tool window.
     */
    public static void attach(final WindowExtendedBuildTool window)
    {
        final ButtonImage toggle = new ButtonImage();
        toggle.setID("interregne_build_mode");
        toggle.setSize(130, 17);
        toggle.setPosition(145, 219);
        refresh(toggle);
        toggle.setHandler(button -> {
            buildNow = !buildNow;
            refresh(toggle);
        });
        window.addChild(toggle);
    }

    /**
     * Tell the server which mode applies, just before a survival placement is sent.
     */
    public static void beforePlacement(final BuildToolPlacementMessage.HandlerType type)
    {
        if (type == BuildToolPlacementMessage.HandlerType.Survival)
        {
            PacketDistributor.sendToServer(new PlacementModePayload(buildNow));
        }
    }

    private static void refresh(final ButtonImage toggle)
    {
        toggle.setImage(buildNow ? TEXTURE : TEXTURE_OFF);
        toggle.setText(Component.literal(buildNow ? "⚒ Construire maintenant" : "Poser le plan seul"));
        toggle.setColors(0x000000);
    }
}
