package fr.kodistan.interregne.region;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * A regional crate as an item: heavy, not craftable, needed for buildings from level 2.
 */
public class CrateItem extends BlockItem
{
    private final Crate crate;

    public CrateItem(final Crate crate, final Block block, final Properties properties)
    {
        super(block, properties);
        this.crate = crate;
    }

    public Crate crate()
    {
        return crate;
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context, final List<Component> tooltip, final TooltipFlag flag)
    {
        tooltip.add(Component.literal("Produite par " + crate.regionName()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Matériau de construction, lourd à porter").withStyle(ChatFormatting.DARK_GRAY));
    }
}
