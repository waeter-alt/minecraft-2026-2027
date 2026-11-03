package fr.kodistan.interregne.client;

import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.gear.DamagedGear;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * Damaged (abîmé) gear shows its name in red and a repair hint.
 */
@EventBusSubscriber(modid = InterregneMod.MODID, value = Dist.CLIENT)
public final class DamagedGearTooltip
{
    private DamagedGearTooltip()
    {
    }

    @SubscribeEvent
    public static void onTooltip(final ItemTooltipEvent event)
    {
        if (!DamagedGear.isDamaged(event.getItemStack()))
        {
            return;
        }
        final List<Component> tooltip = event.getToolTip();
        if (tooltip.isEmpty())
        {
            return;
        }
        tooltip.set(0, tooltip.get(0).copy().withStyle(ChatFormatting.RED));
        tooltip.add(1, Component.literal(DamagedGear.DAMAGED_TOOLTIP).withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
    }
}
