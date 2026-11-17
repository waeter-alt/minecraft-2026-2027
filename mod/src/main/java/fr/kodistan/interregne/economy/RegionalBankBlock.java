package fr.kodistan.interregne.economy;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;

/**
 * The hut of the Banque régionale: placed like any MineColonies hut, built and upgraded from its own blueprint pack.
 */
public class RegionalBankBlock extends AbstractBlockHut<RegionalBankBlock>
{
    @Override
    public String getHutName()
    {
        return "blockhutbanqueregionale";
    }

    @Override
    public BuildingEntry getBuildingEntry()
    {
        return RegionalBanks.BANK_ENTRY.get();
    }
}
