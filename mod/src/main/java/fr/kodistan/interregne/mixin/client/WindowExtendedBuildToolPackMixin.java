package fr.kodistan.interregne.mixin.client;

import com.ldtteam.structurize.client.gui.WindowExtendedBuildTool;
import fr.kodistan.interregne.client.catalogue.SharedPackOrigin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Placement of a blueprint from the shared pack (Relais commercial): send that pack, not the selected style, or the server looks for the
 * plan in a style that does not have it.
 */
@Mixin(value = WindowExtendedBuildTool.class, remap = false)
public abstract class WindowExtendedBuildToolPackMixin
{
    @Redirect(method = "handlePlacement", at = @At(value = "FIELD", opcode = org.objectweb.asm.Opcodes.GETSTATIC,
      target = "Lcom/ldtteam/structurize/client/gui/WindowExtendedBuildTool;currentStructurePack:Ljava/lang/String;"))
    private String interregne$packOfPlacedBlueprint()
    {
        return SharedPackOrigin.packFor(currentStructurePack);
    }

    @org.spongepowered.asm.mixin.Shadow
    private static String currentStructurePack;
}
