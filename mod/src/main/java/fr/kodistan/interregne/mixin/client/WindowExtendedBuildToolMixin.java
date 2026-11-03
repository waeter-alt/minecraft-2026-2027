package fr.kodistan.interregne.mixin.client;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.client.gui.WindowExtendedBuildTool;
import com.ldtteam.structurize.network.messages.BuildToolPlacementMessage;
import fr.kodistan.interregne.client.BuildToolMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Build tool window: add the "Construire maintenant / Poser le plan" toggle and send the choice with each placement.
 */
@Mixin(value = WindowExtendedBuildTool.class, remap = false)
public abstract class WindowExtendedBuildToolMixin
{
    @Shadow
    @Final
    private BiConsumer<WindowExtendedBuildTool, Blueprint> selectionCallback;

    @Inject(method = "<init>(Lnet/minecraft/core/BlockPos;ILjava/util/function/BiConsumer;Ljava/util/function/Predicate;Lnet/minecraft/core/HolderLookup$Provider;)V", at = @At("RETURN"))
    private void interregne$addModeToggle(
      final BlockPos pos,
      final int groundstyle,
      @Nullable final BiConsumer<WindowExtendedBuildTool, Blueprint> callback,
      @Nullable final Predicate<Blueprint> predicate,
      final HolderLookup.Provider provider,
      final CallbackInfo ci)
    {
        // With a selection callback the window only picks a blueprint and never places anything
        if (selectionCallback == null)
        {
            BuildToolMode.attach((WindowExtendedBuildTool) (Object) this);
        }
    }

    @Inject(method = "handlePlacement", at = @At("HEAD"))
    private void interregne$sendMode(final BuildToolPlacementMessage.HandlerType type, final String id, final CallbackInfo ci)
    {
        BuildToolMode.beforePlacement(type);
    }
}
