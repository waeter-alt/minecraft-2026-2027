package fr.kodistan.interregne.dev;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.BlockPosUtil;
import fr.kodistan.interregne.economy.RegionalTreasury;
import fr.kodistan.interregne.war.WarAssaults;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The war slice: two légionnaires assault the colony itself. The checks are the user's war rules:
 * <ul>
 *   <li>the troops march, fight the defenders and bring a building down;</li>
 *   <li>the hut block survives and the building stays registered (the rigorous-rebuild contract);</li>
 *   <li>terrain and plants are untouched around the destroyed building;</li>
 *   <li>the attacker's account receives the loot;</li>
 *   <li>someone fought back (guards/citizens engaged).</li>
 * </ul>
 * Needs about 8000 ticks: travel from the border ring takes a while, then the battering.
 */
public class WarScenario implements Scenario
{
    private IBuilding target;
    private long startTreasury;
    private int startTargets;
    /** States of a +2 rim around the target at setup: the verdict proves the sweep never left the box. */
    private final java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> rim = new java.util.HashMap<>();

    @Override
    public List<String> setup(final IColony colony, final ServerLevel level)
    {
        // Central, not small: defenders walk past central buildings, so the fight can actually happen.
        target = colony.getBuildingManager().getBuildings().values().stream()
          .filter(b -> b.getBuildingLevel() > 0 && !b.getID().equals(colony.getCenter()))
          .min(Comparator.comparingDouble(b -> BlockPosUtil.getDistance(b.getID(), colony.getCenter())))
          .orElseThrow(() -> new IllegalStateException("needs a building (any hut level 1+)"));
        snapshotRim(colony.getWorld());

        startTreasury = RegionalTreasury.get(level.getServer()).balance(colony);
        final String refusal = WarAssaults.launch(level, colony, colony, null, 6);
        if (refusal != null)
        {
            throw new IllegalStateException("assault refused: " + refusal);
        }
        startTargets = WarAssaults.targetsLeft(colony);

        final List<String> lines = new ArrayList<>();
        lines.add("target: " + target.getBuildingType().getTranslationKey() + " lvl " + target.getBuildingLevel()
          + " at " + target.getID().toShortString() + ", hut " + level.getBlockState(target.getID()).getBlock());
        lines.add("treasury before: " + startTreasury);
        return lines;
    }

    /** Records every non-air block of a +2 rim around the target (wall blocks, paths included). */
    private void snapshotRim(final net.minecraft.world.level.Level level)
    {
        rim.clear();
        final var corners = target.getCorners();
        final int minX = Math.min(corners.getA().getX(), corners.getB().getX());
        final int maxX = Math.max(corners.getA().getX(), corners.getB().getX());
        final int minZ = Math.min(corners.getA().getZ(), corners.getB().getZ());
        final int maxZ = Math.max(corners.getA().getZ(), corners.getB().getZ());
        final int minY = Math.min(corners.getA().getY(), corners.getB().getY());
        final int maxY = Math.max(corners.getA().getY(), corners.getB().getY());
        for (BlockPos cursor : BlockPos.betweenClosed(
          new BlockPos(minX - 2, minY - 2, minZ - 2), new BlockPos(maxX + 2, maxY + 2, maxZ + 2)))
        {
            final boolean outside = cursor.getX() < minX || cursor.getX() > maxX
              || cursor.getZ() < minZ || cursor.getZ() > maxZ;
            if (outside && !level.getBlockState(cursor).isAir())
            {
                rim.put(cursor.immutable(), level.getBlockState(cursor));
            }
        }
    }

    @Override
    public String snapshot(final IColony colony, final ServerLevel level)
    {
        return WarAssaults.snapshotOf(colony);
    }

    @Override
    public List<String> verdict(final IColony colony, final ServerLevel level)
    {
        final List<String> lines = new ArrayList<>();
        final int destroyed = WarAssaults.destroyed(colony);
        final long paid = WarAssaults.lootPaid(colony);
        final long nowTreasury = RegionalTreasury.get(level.getServer()).balance(colony);

        if (destroyed > 0)
        {
            lines.add("PASS un bâtiment est tombé (" + destroyed + ")");
        }
        else
        {
            lines.add("FAIL aucun bâtiment détruit — " + snapshot(colony, level));
        }

        final BlockPos hut = target.getID();
        if (level.getBlockState(hut).isAir())
        {
            lines.add("FAIL le bloc de hutte a sauté — la reconstruction rigoureuse est compromise");
        }
        else if (colony.getBuildingManager().getBuilding(hut) == null)
        {
            lines.add("FAIL le bâtiment n'est plus enregistré dans la colonie");
        }
        else
        {
            lines.add("PASS hutte intacte et bâtiment toujours enregistré");
        }

        if (destroyed > 0)
        {
            long changed = 0;
            for (final var entry : rim.entrySet())
            {
                if (level.getBlockState(entry.getKey()) != entry.getValue())
                {
                    changed++;
                }
            }
            if (changed == 0)
            {
                lines.add("PASS le pourtour est intact (rien de détruit hors bâtiment, " + rim.size() + " blocs comparés)");
            }
            else
            {
                lines.add("FAIL " + changed + " blocs du pourtour ont changé — le sweep a débordé");
            }
        }

        if (paid > 0 && nowTreasury >= startTreasury + paid)
        {
            lines.add("PASS butin crédité au trésor (+" + paid + ")");
        }
        else if (destroyed > 0)
        {
            lines.add("FAIL butin non crédité (payé " + paid + ", trésor " + startTreasury + " → " + nowTreasury + ")");
        }

        if (WarAssaults.engaged(colony))
        {
            lines.add("PASS défense engagée (gardes ou villageois ont combattu)");
        }
        else
        {
            lines.add("FAIL personne n'a combattu les troupes");
        }

        final String end = snapshot(colony, level);
        if (WarAssaults.hasAssault(colony))
        {
            lines.add("INFO assaut toujours actif à la fin du temps: " + end);
        }
        return lines;
    }
}
