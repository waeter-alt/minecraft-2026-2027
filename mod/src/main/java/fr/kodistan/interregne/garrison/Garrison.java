package fr.kodistan.interregne.garrison;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.colony.buildings.modules.GuardBuildingModule;
import fr.kodistan.interregne.economy.TreasuryPayment;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Buying guards. Colonies no longer hire guards from their own population: a légionnaire is bought at the combat academy,
 * an archère at the archery, and both go to a free place in a guard tower or a barracks tower. The troop level is the level
 * of the hut that trains them, and sets the guard's skills and price.
 */
public final class Garrison
{
    public static final int LEGIONNAIRE = 0;
    public static final int ARCHERE = 1;

    /**
     * Price of one guard by troop level (1 to 5).
     */
    private static final long[] PRICE = {60, 120, 220, 360, 550};

    /**
     * Every skill of a bought guard starts at least here, by troop level.
     */
    private static final int[] SKILL = {10, 20, 30, 40, 50};

    private Garrison()
    {
    }

    public static String hut(final int kind)
    {
        return kind == ARCHERE ? "archery" : "combatacademy";
    }

    public static JobEntry job(final int kind)
    {
        return kind == ARCHERE ? ModJobs.archer.get() : ModJobs.knight.get();
    }

    public static int troopLevel(final IColony colony, final int kind)
    {
        int best = 0;
        for (final IBuilding building : colony.getBuildingManager().getBuildings().values())
        {
            if (building.getBuildingType().getRegistryName().getPath().equals(hut(kind)))
            {
                best = Math.max(best, building.getBuildingLevel());
            }
        }
        return Math.min(best, PRICE.length);
    }

    public static long price(final int level)
    {
        return level <= 0 ? 0 : PRICE[Math.min(level, PRICE.length) - 1];
    }

    /**
     * Places in all guard and barracks towers. A tower's places are shared by its kinds.
     */
    public static int cap(final IColony colony)
    {
        int cap = 0;
        for (final IBuilding building : colony.getBuildingManager().getBuildings().values())
        {
            int places = 0;
            for (final GuardBuildingModule module : building.getModulesByType(GuardBuildingModule.class))
            {
                places = Math.max(places, module.getModuleMax());
            }
            cap += places;
        }
        return cap;
    }

    /**
     * Guards in all towers, of one kind or (with -1) of all.
     */
    public static int count(final IColony colony, final int kind)
    {
        int count = 0;
        for (final IBuilding building : colony.getBuildingManager().getBuildings().values())
        {
            for (final GuardBuildingModule module : building.getModulesByType(GuardBuildingModule.class))
            {
                if (kind < 0 || module.getJobEntry() == job(kind))
                {
                    count += module.getAssignedCitizen().size();
                }
            }
        }
        return count;
    }

    /**
     * A tower module of this kind with a free place, the least crowded tower first.
     */
    @Nullable
    private static GuardBuildingModule freePlace(final IColony colony, final int kind)
    {
        GuardBuildingModule best = null;
        int bestLoad = Integer.MAX_VALUE;
        for (final IBuilding building : colony.getBuildingManager().getBuildings().values())
        {
            if (building.getBuildingLevel() <= 0)
            {
                continue;
            }
            for (final GuardBuildingModule module : building.getModulesByType(GuardBuildingModule.class))
            {
                final int load = building.getAllAssignedCitizen().size();
                if (module.getJobEntry() == job(kind) && !module.isFull() && load < bestLoad)
                {
                    best = module;
                    bestLoad = load;
                }
            }
        }
        return best;
    }

    /**
     * Buys one guard, or says why not.
     */
    @Nullable
    public static String buy(final ServerPlayer player, final IColony colony, final int kind)
    {
        final int level = troopLevel(colony, kind);
        if (level <= 0)
        {
            return kind == ARCHERE ? "Construisez d'abord le champ de tir." : "Construisez d'abord l'académie de combat.";
        }
        final GuardBuildingModule module = freePlace(colony, kind);
        if (module == null)
        {
            return "Plus de place dans les tours : construisez une tour de garde ou agrandissez la caserne.";
        }
        final TreasuryPayment payment = TreasuryPayment.pay(player, colony, price(level));
        if (payment == null)
        {
            return "Pas assez de couronnes.";
        }
        final ICitizenData citizen = colony.getCitizenManager().createAndRegisterCivilianData();
        if (citizen == null)
        {
            payment.refund(player, colony);
            return "Recrutement impossible pour l'instant.";
        }
        citizen.setGenderAndGenerateName(kind == ARCHERE);
        for (final Skill skill : Skill.values())
        {
            final int missing = SKILL[level - 1] - citizen.getCitizenSkillHandler().getLevel(skill);
            if (missing > 0)
            {
                citizen.getCitizenSkillHandler().incrementLevel(skill, missing);
            }
        }
        GarrisonRoster.get(player.server).add(colony, citizen, kind, level);
        module.assignCitizen(citizen);
        colony.getCitizenManager().spawnOrCreateCivilian(citizen, colony.getWorld(), module.getBuilding().getPosition(), true);
        return null;
    }
}
