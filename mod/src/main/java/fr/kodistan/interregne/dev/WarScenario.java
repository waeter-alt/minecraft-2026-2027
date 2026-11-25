package fr.kodistan.interregne.dev;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.BlockPosUtil;
import fr.kodistan.interregne.economy.RegionalTreasury;
import fr.kodistan.interregne.war.WarAssaults;
import net.minecraft.core.BlockPos;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import fr.kodistan.interregne.war.AssaultTroop;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.util.ArrayList;
import java.util.List;

/**
 * The war slice: a handful of troops assault the colony itself. Same checks regardless of composition (see
 * {@link fr.kodistan.interregne.dev.ScenarioRunner}'s {@code war} / {@code war-archers} entries) — the user's war
 * rules:
 * <ul>
 *   <li>the troops march, fight the defenders and bring a building down;</li>
 *   <li>the hut block survives and the building stays registered (the rigorous-rebuild contract);</li>
 *   <li>terrain and plants are untouched around the destroyed building;</li>
 *   <li>the attacker's account receives the loot;</li>
 *   <li>someone fought back (guards/citizens engaged);</li>
 *   <li>the fall order: wall first, then nearest to the rally point, town hall last;</li>
 *   <li>a fighting guard within 24 blocks freezes the targeted building's structure points (watched every tick,
 *       plus a forced window with a guard teleported next to the target and marked as fighting);</li>
 *   <li>shared aggro: a troop that gets hit calls a neighbour within 16 blocks that has no target;</li>
 *   <li>the clock: positive at launch, pushed back when a building falls.</li>
 * </ul>
 * Needs about 8000 ticks: travel from the border ring takes a while, then the battering.
 */
public class WarScenario implements Scenario
{
    private final int legionnaires;
    private final int archers;

    private IBuilding target;
    private long startTreasury;
    private int startTargets;
    /** States of a +2 rim around the target at setup: the verdict proves the sweep never left the box. */
    private final java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> rim = new java.util.HashMap<>();

    private final List<String> earlyLines = new ArrayList<>();
    // Freeze watch (every tick, on the current first target).
    private BlockPos prevCur;
    private int prevSp;
    private boolean prevHolds;
    private long lastDropTick = -1000;
    private long heldTicks;
    private long heldDrops;
    // Forced freeze window: a guard teleported next to the target and stamped as fighting.
    private static final int FORCE_TICKS = 100;
    private int forcePhase;
    private long forceStart;
    private BlockPos forceTarget;
    private int forceSp;
    private AbstractEntityCitizen forceGuard;
    private String forceLine;
    private boolean aggroDone;
    private String aggroLine;
    private int prevDestroyed;
    private long prevSeconds;
    private int clockFalls;
    private int clockFails;
    private String clockFailDetail = "";

    private boolean sawEngaged;
    private int sawDestroyed;
    private long sawLoot;

    public WarScenario()
    {
        this(6, 0);
    }

    public WarScenario(final int legionnaires, final int archers)
    {
        this.legionnaires = legionnaires;
        this.archers = archers;
    }

    @Override
    public List<String> setup(final IColony colony, final ServerLevel level)
    {
        if (colony.getBuildingManager().getBuildings().values().stream()
          .noneMatch(b -> b.getBuildingLevel() > 0 && !b.getID().equals(colony.getCenter())))
        {
            throw new IllegalStateException("needs a building (any hut level 1+)");
        }

        startTreasury = RegionalTreasury.get(level.getServer()).balance(colony);
        final String refusal = WarAssaults.launch(level, colony, colony, null, legionnaires, archers);
        if (refusal != null)
        {
            throw new IllegalStateException("assault refused: " + refusal);
        }
        startTargets = WarAssaults.targetsLeft(colony);

        // The first building of the fall order is the one that falls first: the hut and rim checks must watch a
        // building that actually gets attacked (nothing has been touched yet, the troops are still marching).
        final List<BlockPos> order = WarAssaults.targetOrder(colony);
        target = order.stream()
          .filter(p -> !p.equals(colony.getCenter()))
          .map(p -> colony.getBuildingManager().getBuilding(p))
          .filter(java.util.Objects::nonNull)
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("no building among the assault's targets"));
        snapshotRim(colony.getWorld());
        checkOrder(colony, order);
        prevSeconds = WarAssaults.secondsLeft(colony);
        earlyLines.add(prevSeconds > 0
          ? "PASS chrono positif au lancement (" + prevSeconds + " s)"
          : "FAIL chrono nul au lancement (" + prevSeconds + " s)");

        final List<String> lines = new ArrayList<>();
        lines.add("target: " + target.getBuildingType().getTranslationKey() + " lvl " + target.getBuildingLevel()
          + " at " + target.getID().toShortString() + ", hut " + level.getBlockState(target.getID()).getBlock());
        lines.add("treasury before: " + startTreasury);
        return lines;
    }

    /** Fall order: the wall (not a building) only first, buildings by increasing distance to the rally point, town hall last. */
    private void checkOrder(final IColony colony, final List<BlockPos> order)
    {
        final BlockPos rally = WarAssaults.rallyPoint(colony);
        if (rally == null)
        {
            earlyLines.add("FAIL point de ralliement inconnu");
            return;
        }
        final List<BlockPos> buildings = new ArrayList<>();
        boolean wallMisplaced = false;
        for (int i = 0; i < order.size(); i++)
        {
            final BlockPos pos = order.get(i);
            if (colony.getBuildingManager().getBuilding(pos) == null)
            {
                wallMisplaced |= i != 0;
            }
            else
            {
                buildings.add(pos);
            }
        }
        final boolean hasWall = buildings.size() < order.size();
        earlyLines.add(wallMisplaced ? "FAIL la muraille n'est pas la première cible"
          : "PASS ordre de chute : " + (hasWall ? "muraille en premier, " : "pas de muraille, ") + buildings.size() + " bâtiment(s)");

        final BlockPos center = colony.getCenter();
        final List<BlockPos> others = new ArrayList<>(buildings);
        others.remove(center);
        boolean sorted = true;
        final StringBuilder dists = new StringBuilder();
        double previous = -1;
        for (final BlockPos pos : others)
        {
            final double d = BlockPosUtil.getDistance(pos, rally);
            sorted &= d >= previous;
            previous = d;
            dists.append(String.format(java.util.Locale.ROOT, " %.0f", d));
        }
        earlyLines.add(sorted ? "PASS bâtiments triés par distance croissante au ralliement (" + others.size() + " :" + dists + ")"
          : "FAIL bâtiments mal triés par distance au ralliement :" + dists);

        if (!buildings.contains(center))
        {
            earlyLines.add("INFO l'hôtel de ville n'est pas une cible");
        }
        else if (buildings.get(buildings.size() - 1).equals(center))
        {
            earlyLines.add("PASS l'hôtel de ville est la dernière cible");
        }
        else
        {
            earlyLines.add("FAIL l'hôtel de ville n'est pas la dernière cible");
        }
    }

    @Override
    public void tick(final IColony colony, final ServerLevel level, final long tick)
    {
        if (!WarAssaults.hasAssault(colony))
        {
            return;
        }
        // Latched: the assault's own readings vanish the moment it ends (pushed back, timed out).
        sawEngaged |= WarAssaults.engaged(colony);
        sawDestroyed = Math.max(sawDestroyed, WarAssaults.destroyed(colony));
        sawLoot = Math.max(sawLoot, WarAssaults.lootPaid(colony));
        watchClock(colony);
        watchFreeze(colony, tick);
        if (!aggroDone && WarAssaults.engaged(colony))
        {
            testAggro(level);
        }
    }

    private void watchClock(final IColony colony)
    {
        final long seconds = WarAssaults.secondsLeft(colony);
        final int destroyed = WarAssaults.destroyed(colony);
        if (destroyed > prevDestroyed)
        {
            clockFalls++;
            // addTime = min(now + cap, deadline + 90 s); a tick (read rounded up to the second) separates the readings.
            final long expected = Math.min(prevSeconds + 90, WarAssaults.clockCapSeconds()) - 2;
            if (seconds < expected)
            {
                clockFails++;
                clockFailDetail = " (" + prevSeconds + " s -> " + seconds + " s, attendu >= " + expected + ")";
            }
        }
        prevDestroyed = destroyed;
        prevSeconds = seconds;
    }

    /** Watches the first target's structure points: while a guard holds it on two consecutive ticks, they must not drop. */
    private void watchFreeze(final IColony colony, final long tick)
    {
        final List<BlockPos> order = WarAssaults.targetOrder(colony);
        final BlockPos cur = order.isEmpty() ? null : order.get(0);
        final boolean building = cur != null && colony.getBuildingManager().getBuilding(cur) != null;
        final int sp = cur == null ? -1 : WarAssaults.structureLeft(colony, cur);
        final boolean holds = building && WarAssaults.guardHolding(colony, cur);
        final boolean same = cur != null && cur.equals(prevCur);
        if (same && sp < prevSp)
        {
            lastDropTick = tick;
            if (prevHolds && holds)
            {
                heldDrops++;
            }
        }
        if (same && prevHolds && holds)
        {
            heldTicks++;
        }

        // Forced window, once the troops are visibly battering the target: the control is the drop seen just before.
        if (forcePhase == 0 && building && same && tick - lastDropTick <= 40 && !holds)
        {
            final List<AbstractEntityCitizen> guards = WarAssaults.guardsOf(colony);
            if (!guards.isEmpty())
            {
                forcePhase = 1;
                forceStart = tick;
                forceTarget = cur;
                forceSp = sp;
                forceGuard = guards.get(0);
            }
        }
        if (forcePhase == 1)
        {
            if (cur == null || !cur.equals(forceTarget) || !forceGuard.isAlive())
            {
                forcePhase = 2;
                forceLine = "INFO fenêtre de gel interrompue (cible tombée ou garde mort) — non concluant";
            }
            else
            {
                forceGuard.teleportTo(cur.getX() + 3.5D, cur.getY() + 1, cur.getZ() + 0.5D);
                WarAssaults.debugStampFight(colony, forceGuard);
                if (sp < forceSp)
                {
                    forcePhase = 2;
                    forceLine = "FAIL la cible a perdu " + (forceSp - sp) + " point(s) de structure malgré un garde au combat à 3 blocs";
                }
                else if (tick - forceStart >= FORCE_TICKS)
                {
                    forcePhase = 2;
                    forceLine = "PASS un garde au combat gèle le bâtiment visé (" + forceSp + " points inchangés pendant "
                      + FORCE_TICKS + " ticks, après des coups qui le faisaient baisser)";
                }
            }
        }
        prevCur = cur;
        prevSp = sp;
        prevHolds = holds;
    }

    /** Hurts one troop with a non-troop attacker, then looks at a neighbour (<= 16 blocks) and a distant troop. */
    private void testAggro(final ServerLevel level)
    {
        final List<AssaultTroop> troops = new ArrayList<>(level.getEntities(EntityTypeTest.forClass(AssaultTroop.class), Entity::isAlive));
        AssaultTroop hurt = null;
        AssaultTroop near = null;
        for (final AssaultTroop a : troops)
        {
            for (final AssaultTroop b : troops)
            {
                if (a != b && a.distanceTo(b) <= 12.0F)
                {
                    hurt = a;
                    near = b;
                    break;
                }
            }
            if (near != null)
            {
                break;
            }
        }
        if (near == null)
        {
            return;
        }
        aggroDone = true;
        AssaultTroop far = null;
        for (final AssaultTroop c : troops)
        {
            if (c != hurt && c != near && c.distanceTo(hurt) > 20.0F)
            {
                far = c;
                break;
            }
        }
        final Sheep attacker = EntityType.SHEEP.create(level);
        if (attacker == null)
        {
            aggroLine = "INFO aggro non testée (mouton de test non créé)";
            return;
        }
        attacker.moveTo(hurt.getX() + 1, hurt.getY(), hurt.getZ());
        near.setTarget(null);
        if (far != null)
        {
            far.setTarget(null);
        }
        hurt.invulnerableTime = 0;
        final float before = hurt.getHealth();
        hurt.hurt(level.damageSources().mobAttack(attacker), 0.5F);
        final boolean damaged = hurt.getHealth() < before;
        final boolean nearCalled = near.getTarget() == attacker;
        final boolean farCalled = far != null && far.getTarget() == attacker;
        final String gap = String.format(java.util.Locale.ROOT, "%.1f", near.distanceTo(hurt));
        if (!damaged)
        {
            aggroLine = "INFO aggro non concluante : le coup de test n'a pas blessé la troupe";
        }
        else if (!nearCalled)
        {
            aggroLine = "FAIL la voisine à " + gap + " blocs, sans cible, n'a pas pris le frappeur pour cible";
        }
        else if (farCalled)
        {
            aggroLine = "FAIL une troupe à plus de 20 blocs a aussi été appelée";
        }
        else
        {
            aggroLine = "PASS aggro partagée : la voisine à " + gap + " blocs prend le frappeur pour cible"
              + (far != null ? ", la lointaine ne bouge pas" : " (aucune troupe lointaine à vérifier)");
        }
        // The attacker never joins the world: don't leave troops chasing it.
        hurt.setTarget(null);
        near.setTarget(null);
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
        final List<String> lines = new ArrayList<>(earlyLines);
        final int destroyed = Math.max(sawDestroyed, WarAssaults.destroyed(colony));
        final long paid = Math.max(sawLoot, WarAssaults.lootPaid(colony));
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

        if (sawEngaged || WarAssaults.engaged(colony))
        {
            lines.add("PASS défense engagée (gardes ou villageois ont combattu)");
        }
        else
        {
            lines.add("FAIL personne n'a combattu les troupes");
        }

        // Légionnaires batter from right against the wall (2.5 blocks); archers deliberately stand back
        // (WarAssaults.RANGED_STANDOFF, up to 8) instead of pressing up against it like a pillager.
        final double maxDistance = archers > 0 ? 9.0D : 2.5D;
        final double[] strikes = WarAssaults.strikeDistances(colony);
        if (strikes == null)
        {
            lines.add("FAIL aucune troupe n'a frappé de mur");
        }
        else if (strikes[0] <= maxDistance)
        {
            lines.add(String.format(java.util.Locale.ROOT, "PASS troupes à bonne distance du mur (pire distance %.2f sur %d premières frappes)",
              strikes[0], (int) strikes[1]));
        }
        else
        {
            lines.add(String.format(java.util.Locale.ROOT, "FAIL une troupe frappe de trop loin (pire distance %.2f sur %d premières frappes)",
              strikes[0], (int) strikes[1]));
        }

        if (clockFalls == 0)
        {
            lines.add("INFO chrono : aucun bâtiment tombé, poussée non vérifiée");
        }
        else if (clockFails == 0)
        {
            lines.add("PASS chrono repoussé à chaque chute (" + clockFalls + " chute(s))");
        }
        else
        {
            lines.add("FAIL chrono non repoussé à " + clockFails + " chute(s) sur " + clockFalls + clockFailDetail);
        }

        if (heldDrops > 0)
        {
            lines.add("FAIL " + heldDrops + " baisse(s) de structure alors qu'un garde tenait la cible");
        }
        else if (heldTicks > 0)
        {
            lines.add("PASS aucune baisse de structure pendant " + heldTicks + " tick(s) tenus par un garde");
        }
        if (forceLine != null)
        {
            lines.add(forceLine);
        }
        else if (forcePhase == 1)
        {
            lines.add("INFO fenêtre de gel en cours à la fin du temps — non concluant");
        }
        else
        {
            lines.add("INFO gel par un garde non testé : aucun garde dans la colonie, ou cible jamais battue");
        }
        lines.add(aggroLine != null ? aggroLine : "INFO aggro partagée non testée : jamais deux troupes proches après un premier coup");

        final String end = snapshot(colony, level);
        if (WarAssaults.hasAssault(colony))
        {
            lines.add("INFO assaut toujours actif à la fin du temps: " + end);
        }
        return lines;
    }
}
