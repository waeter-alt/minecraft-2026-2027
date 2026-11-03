package fr.kodistan.interregne.colony;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import fr.kodistan.interregne.Config;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Citizens' equipment rules: tools, weapons and armor that never wear out, so villagers stop asking for new ones.
 */
public final class CitizenEquipment
{
    private CitizenEquipment()
    {
    }

    public static boolean isUnbreakable(@Nullable final LivingEntity entity, final ItemStack stack)
    {
        if (!(entity instanceof AbstractEntityCitizen))
        {
            return false;
        }
        try
        {
            return stack.getItem() instanceof ArmorItem ? Config.UNBREAKABLE_ARMOR.get() : Config.UNBREAKABLE_TOOLS.get();
        }
        catch (final IllegalStateException e)
        {
            // Server config not loaded yet: keep vanilla behavior
            return false;
        }
    }
}
