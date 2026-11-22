package fr.kodistan.interregne.colony;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Huts a colony may have only once: the town hall, the warehouse, the relais commercial, the bank and the depot. Placing a second
 * one is refused when the blueprint is put down ({@code SurvivalHandlerMixin}) and greyed in the catalogue, so a duplicate never
 * exists. A new unique hut is added to {@link #LABELS}.
 */
public final class UniqueHuts
{
    /**
     * Building id (the registry path) to how the message names it, with its article.
     */
    private static final Map<String, String> LABELS = Map.of(
      "townhall", "hôtel de ville",
      "warehouse", "entrepôt",
      "relais_commercial", "relais commercial",
      "banque_regionale", "banque régionale",
      "depot_regional", "dépôt régional",
      "barracks", "caserne");

    private UniqueHuts()
    {
    }

    public static boolean isUnique(final String buildingId)
    {
        return LABELS.containsKey(buildingId);
    }

    /**
     * The sentence for a second one of this building, saying where the first stands.
     */
    public static String message(final String buildingId, final String colonyName, final BlockPos existing)
    {
        final String label = LABELS.get(buildingId);
        final boolean feminine = label.startsWith("banque") || label.equals("caserne");
        return "Une colonie n'a qu'" + (feminine ? "une " : "un ") + label + " : " + (feminine ? "celle" : "celui") + " de " + colonyName + " est déjà en " + existing.toShortString() + ".";
    }

    /**
     * Why this hut cannot be placed in the colony, or null when it can: a colony already has one of it, whatever its level or
     * whether it is still being built.
     */
    @Nullable
    public static String refusal(@Nullable final IColony colony, final Block block)
    {
        if (colony == null || !(block instanceof AbstractBlockHut<?> hut))
        {
            return null;
        }
        final String id = hut.getBuildingEntry().getRegistryName().getPath();
        if (!isUnique(id))
        {
            return null;
        }
        for (final IBuilding existing : colony.getBuildingManager().getBuildings().values())
        {
            if (existing.getBuildingType() == hut.getBuildingEntry())
            {
                return message(id, colony.getName(), existing.getPosition());
            }
        }
        return null;
    }
}
