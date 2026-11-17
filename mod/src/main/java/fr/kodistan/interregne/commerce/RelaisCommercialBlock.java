package fr.kodistan.interregne.commerce;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;

/**
 * The hut of the Relais commercial: placed like any MineColonies hut, built and upgraded from its own blueprint pack.
 */
public class RelaisCommercialBlock extends AbstractBlockHut<RelaisCommercialBlock>
{
    @Override
    public String getHutName()
    {
        return "blockhutrelaiscommercial";
    }

    @Override
    public BuildingEntry getBuildingEntry()
    {
        return Commerce.RELAIS_ENTRY.get();
    }
}
