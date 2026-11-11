package fr.kodistan.interregne.dev;

import com.minecolonies.api.colony.IColony;
import fr.kodistan.interregne.walls.WallRegistry;
import fr.kodistan.interregne.walls.WallSpawnGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * No day/night cycle needed: {@link WallSpawnGuard} is a pure check per spawn attempt, so this drives it directly
 * instead of waiting for a real monster to roll a spawn. Runs for a single tick.
 */
public final class WallSpawnScenario implements Scenario
{
    @Override
    public List<String> setup(final IColony colony, final ServerLevel level)
    {
        WallRegistry.get(level.getServer())
          .put(colony, new WallRegistry.Wall(List.of(), List.of(), false, 1, new HashMap<>(), new long[0], 0));
        return List.of("fake wall registered for " + colony.getName());
    }

    @Override
    public String snapshot(final IColony colony, final ServerLevel level)
    {
        return "n/a";
    }

    @Override
    public List<String> verdict(final IColony colony, final ServerLevel level)
    {
        final List<String> lines = new ArrayList<>();
        final BlockPos at = colony.getCenter();
        lines.add(check("un zombi ne spawn pas dans une colonie murée",
          result(level, EntityType.ZOMBIE, MobSpawnType.NATURAL, at) == MobSpawnEvent.SpawnPlacementCheck.Result.FAIL));

        WallRegistry.get(level.getServer()).remove(colony);
        lines.add(check("un zombi spawn normalement sans muraille",
          result(level, EntityType.ZOMBIE, MobSpawnType.NATURAL, at) == MobSpawnEvent.SpawnPlacementCheck.Result.DEFAULT));

        WallRegistry.get(level.getServer())
          .put(colony, new WallRegistry.Wall(List.of(), List.of(), false, 1, new HashMap<>(), new long[0], 0));
        lines.add(check("une vache n'est pas bloquée malgré la muraille",
          result(level, EntityType.COW, MobSpawnType.NATURAL, at) == MobSpawnEvent.SpawnPlacementCheck.Result.DEFAULT));
        lines.add(check("un spawner n'est pas bloqué par la muraille",
          result(level, EntityType.ZOMBIE, MobSpawnType.SPAWNER, at) == MobSpawnEvent.SpawnPlacementCheck.Result.DEFAULT));

        WallRegistry.get(level.getServer()).remove(colony);
        return lines;
    }

    private static MobSpawnEvent.SpawnPlacementCheck.Result result(final ServerLevel level, final EntityType<?> type, final MobSpawnType spawnType,
      final BlockPos at)
    {
        final MobSpawnEvent.SpawnPlacementCheck event = new MobSpawnEvent.SpawnPlacementCheck(type, level, spawnType, at, level.getRandom(), true);
        WallSpawnGuard.onSpawnPlacementCheck(event);
        return event.getResult();
    }

    private static String check(final String what, final boolean pass)
    {
        return (pass ? "PASS " : "FAIL ") + what;
    }
}
