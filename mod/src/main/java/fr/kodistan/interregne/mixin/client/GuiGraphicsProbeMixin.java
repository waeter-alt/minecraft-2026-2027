package fr.kodistan.interregne.mixin.client;

import fr.kodistan.interregne.client.dev.LayoutProbe;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Feeds {@link LayoutProbe} while a dev capture checks a screen. Does nothing the rest of the time.
 */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsProbeMixin
{
    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;FFIZ)I", at = @At("HEAD"))
    private void interregne$probeString(final Font font, final String text, final float x, final float y, final int color, final boolean shadow, final CallbackInfoReturnable<Integer> cir)
    {
        if (LayoutProbe.active())
        {
            LayoutProbe.text((GuiGraphics) (Object) this, font, text, x, y);
        }
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;FFIZ)I", at = @At("HEAD"))
    private void interregne$probeSequence(final Font font, final FormattedCharSequence text, final float x, final float y, final int color, final boolean shadow, final CallbackInfoReturnable<Integer> cir)
    {
        if (LayoutProbe.active())
        {
            LayoutProbe.text((GuiGraphics) (Object) this, font, text, x, y);
        }
    }

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V", at = @At("HEAD"))
    private void interregne$probeItem(final LivingEntity entity, final Level level, final ItemStack stack, final int x, final int y, final int seed, final int guiOffset, final CallbackInfo ci)
    {
        if (LayoutProbe.active() && !stack.isEmpty())
        {
            LayoutProbe.item((GuiGraphics) (Object) this, x, y);
        }
    }

    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("HEAD"))
    private void interregne$decorationsIn(final Font font, final ItemStack stack, final int x, final int y, final String text, final CallbackInfo ci)
    {
        LayoutProbe.decorations(true);
    }

    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("RETURN"))
    private void interregne$decorationsOut(final Font font, final ItemStack stack, final int x, final int y, final String text, final CallbackInfo ci)
    {
        LayoutProbe.decorations(false);
    }

    @Inject(method = "enableScissor", at = @At("HEAD"))
    private void interregne$scissorIn(final int x0, final int y0, final int x1, final int y1, final CallbackInfo ci)
    {
        LayoutProbe.pushScissor(x0, y0, x1, y1);
    }

    @Inject(method = "disableScissor", at = @At("HEAD"))
    private void interregne$scissorOut(final CallbackInfo ci)
    {
        LayoutProbe.popScissor();
    }
}
