package fr.kodistan.interregne.mixin.client;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.client.gui.WindowExtendedBuildTool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Lets the build catalogue hand its chosen blueprint to the build tool.
 */
@Mixin(value = WindowExtendedBuildTool.class, remap = false)
public interface WindowExtendedBuildToolAccessor
{
    @Invoker("setBlueprint")
    void interregne$setBlueprint(Blueprint blueprint);
}
