package fr.kodistan.interregne.colony;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Herders butcher their animals with vanilla kills: multiply the drops of animals a herder killed.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class HerderYields
{
    private HerderYields()
    {
    }

    @SubscribeEvent
    public static void onDrops(final LivingDropsEvent event)
    {
        if (!(event.getSource().getEntity() instanceof AbstractEntityCitizen citizen) || citizen.getCitizenData() == null)
        {
            return;
        }
        final var building = citizen.getCitizenData().getWorkBuilding();
        if (building == null)
        {
            return;
        }

        final List<ItemEntity> originals = new ArrayList<>(event.getDrops());
        final List<ItemStack> stacks = originals.stream().map(ItemEntity::getItem).toList();
        final List<ItemStack> boosted = Yields.boost(building, stacks, citizen.getRandom());
        if (boosted == stacks)
        {
            return;
        }

        event.getDrops().clear();
        final ItemEntity template = originals.isEmpty() ? null : originals.getFirst();
        for (final ItemStack stack : boosted)
        {
            if (template != null)
            {
                event.getDrops().add(new ItemEntity(template.level(), template.getX(), template.getY(), template.getZ(), stack));
            }
        }
    }
}
