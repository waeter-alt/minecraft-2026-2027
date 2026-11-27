package fr.kodistan.interregne.colony;

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.ColonyUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * A hut placed with the build tool must not overlap a building of the colony: MineColonies places it anyway and the
 * builder then flattens the old building in silence (warehouse under the depot, town hall under the academy, playtest
 * 2026-11-27). Both footprints are taken at their maximum level, since a level 1 that fits can still grow into the
 * neighbour.
 */
public final class PlacementOverlap
{
    /**
     * Buildings farther than this from the new footprint are not checked at their maximum level (that means reading
     * their blueprint): no hut grows by that much.
     */
    private static final int MAX_LEVEL_MARGIN = 32;

    private static final int HIGHEST_LEVEL = 5;

    private PlacementOverlap()
    {
    }

    /**
     * Why the blueprint cannot go there, or null when its footprint touches no building of the colony.
     */
    @Nullable
    public static Component refusal(
      @Nullable final IColony colony,
      final Level world,
      final Blueprint blueprint,
      final String packName,
      final String blueprintPath,
      final BlockPos pos,
      final RotationMirror rotMir)
    {
        if (colony == null)
        {
            return null;
        }
        final BoundingBox placed = maxLevelBox(world, blueprint, packName, blueprintPath, pos, rotMir);
        for (final IBuilding building : colony.getBuildingManager().getBuildings().values())
        {
            // The same hut put down again where it stands is MineColonies' own business, not an overlap
            if (building.getPosition().equals(pos))
            {
                continue;
            }
            if (placed.intersects(existingBox(world, building, placed)))
            {
                return Component.literal("Impossible de construire ici : l'emprise touche ")
                  .append(Component.translatable(building.getBuildingDisplayName()))
                  .append(" (" + building.getPosition().toShortString() + ").")
                  .withStyle(ChatFormatting.RED);
            }
        }
        return null;
    }

    private static BoundingBox maxLevelBox(
      final Level world,
      final Blueprint blueprint,
      final String packName,
      final String blueprintPath,
      final BlockPos pos,
      final RotationMirror rotMir)
    {
        final BoundingBox box = box(ColonyUtils.calculateCorners(pos, world, blueprint, rotMir));
        final String stem = blueprintPath.replace(".blueprint", "");
        if (stem.isEmpty() || !Character.isDigit(stem.charAt(stem.length() - 1)))
        {
            return box;
        }
        final int level = stem.charAt(stem.length() - 1) - '0';
        for (int max = HIGHEST_LEVEL; max > level; max--)
        {
            final Blueprint higher = load(world, packName, stem.substring(0, stem.length() - 1) + max + ".blueprint");
            if (higher != null)
            {
                return union(box, box(ColonyUtils.calculateCorners(pos, world, higher, rotMir)));
            }
        }
        return box;
    }

    private static BoundingBox existingBox(final Level world, final IBuilding building, final BoundingBox near)
    {
        BoundingBox box = box(building.getCorners());
        if (!box.inflatedBy(MAX_LEVEL_MARGIN).intersects(near) || building.getBuildingLevel() >= building.getMaxBuildingLevel())
        {
            return box;
        }
        final String stem = building.getBlueprintPath().replace(".blueprint", "");
        if (stem.isEmpty() || !Character.isDigit(stem.charAt(stem.length() - 1)))
        {
            return box;
        }
        final Blueprint max = load(world, building.getStructurePack(), stem.substring(0, stem.length() - 1) + building.getMaxBuildingLevel() + ".blueprint");
        if (max != null)
        {
            box = union(box, box(ColonyUtils.calculateCorners(building.getPosition(), world, max, building.getRotationMirror())));
        }
        return box;
    }

    @Nullable
    private static Blueprint load(final Level world, final String pack, final String path)
    {
        try
        {
            return StructurePacks.getBlueprint(pack, path, true, world.registryAccess());
        }
        catch (final RuntimeException e)
        {
            return null;
        }
    }

    private static BoundingBox box(final Tuple<BlockPos, BlockPos> corners)
    {
        return BoundingBox.fromCorners(corners.getA(), corners.getB());
    }

    private static BoundingBox union(final BoundingBox a, final BoundingBox b)
    {
        return new BoundingBox(Math.min(a.minX(), b.minX()), Math.min(a.minY(), b.minY()), Math.min(a.minZ(), b.minZ()),
          Math.max(a.maxX(), b.maxX()), Math.max(a.maxY(), b.maxY()), Math.max(a.maxZ(), b.maxZ()));
    }
}
