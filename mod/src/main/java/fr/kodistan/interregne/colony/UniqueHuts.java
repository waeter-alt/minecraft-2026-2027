package fr.kodistan.interregne.colony;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Huts a colony may have only once: the relais commercial, the bank and the depot. Placing a second one is refused when the
 * blueprint is put down ({@code SurvivalHandlerMixin}), so a duplicate never exists. A new unique hut is added to {@link #LABELS}.
 */
public final class UniqueHuts
{
    /**
     * Building id (the registry path) to how the message names it, with its article.
     */
    private static final Map<String, String> LABELS = Map.of(
      "relais_commercial", "relais commercial",
      "banque_regionale", "banque régionale",
      "depot_regional", "dépôt régional");

    private UniqueHuts()
    {
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
        final String label = LABELS.get(id);
        if (label == null)
        {
            return null;
        }
        for (final IBuilding existing : colony.getBuildingManager().getBuildings().values())
        {
            if (existing.getBuildingType() == hut.getBuildingEntry())
            {
                return "Une colonie n'a qu'un " + label + " : celui de " + colony.getName() + " est déjà en " + existing.getPosition().toShortString() + ".";
            }
        }
        return null;
    }
}
