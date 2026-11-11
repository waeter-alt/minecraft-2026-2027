package fr.kodistan.interregne.walls;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Récompense d'avoir une muraille : aucun monstre ne spawn naturellement dans la frontière d'une colonie qui en a une
 * (peu importe son état — portes ouvertes ou fermées, cette distinction n'existe pas encore côté gameplay).
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class WallSpawnGuard
{
    private WallSpawnGuard()
    {
    }

    @SubscribeEvent
    public static void onSpawnPlacementCheck(final MobSpawnEvent.SpawnPlacementCheck event)
    {
        if (event.getSpawnType() != MobSpawnType.NATURAL || event.getEntityType().getCategory() != MobCategory.MONSTER
              || !(event.getLevel() instanceof ServerLevel level))
        {
            return;
        }
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, event.getPos());
        if (colony != null && WallRegistry.get(level.getServer()).of(colony) != null)
        {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }
}
