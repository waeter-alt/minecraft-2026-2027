package fr.kodistan.interregne.region;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;

/**
 * The hut of the Dépôt Régional: placed like any MineColonies hut, built and upgraded from its own blueprint pack.
 */
public class DepotRegionalBlock extends AbstractBlockHut<DepotRegionalBlock>
{
    @Override
    public String getHutName()
    {
        return "blockhutdepotregional";
    }

    @Override
    public BuildingEntry getBuildingEntry()
    {
        return Crates.DEPOT_ENTRY.get();
    }
}
