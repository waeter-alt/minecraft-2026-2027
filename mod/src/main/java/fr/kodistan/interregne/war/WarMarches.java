package fr.kodistan.interregne.war;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.garrison.Garrison;
import fr.kodistan.interregne.politics.Alliances;
import fr.kodistan.interregne.politics.Diplomacy;
import fr.kodistan.interregne.routes.RouteRegistry;
import fr.kodistan.interregne.routes.WarRoad;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Troops that left their colony's garrison for a war, walking the war road to the enemy, like a caravan: on paper while
 * nobody is near, as real (inert) soldiers when a player is. They wait for the road while it is being built; if none
 * can be traced they go straight to the enemy. At the end of the road the assault starts with them.
 * <p>
 * Marches live in memory: when the server stops, the soldiers on the road go back to their garrison.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class WarMarches
{
    private static final int CHECK_EVERY = 20;
    private static final String NAME_TAG = "interregne_war_march";

    private static final class March
    {
        final int attacker;
        final int defender;
        final List<Integer> legion;
        final List<Integer> archers;
        @Nullable BlockPos[] path;
        double progress;
        final List<UUID> puppets = new ArrayList<>();
        boolean announced;

        March(final int attacker, final int defender, final List<Integer> legion, final List<Integer> archers)
        {
            this.attacker = attacker;
            this.defender = defender;
            this.legion = legion;
            this.archers = archers;
        }

        int size()
        {
            return legion.size() + archers.size();
        }
    }

    private static final List<March> MARCHES = new ArrayList<>();
    private static int ticks;

    private WarMarches()
    {
    }

    public static boolean marching(final int attacker, final int defender)
    {
        return MARCHES.stream().anyMatch(m -> m.attacker == attacker && m.defender == defender);
    }

    /**
     * Takes the chosen soldiers out of the attacker's garrison (best levels first) and sets them on the road.
     *
     * @return a human-readable refusal, or null when the troops are on their way.
     */
    @Nullable
    public static String start(final MinecraftServer server, final IColony attacker, final IColony defender, final int legionnaires, final int archers)
    {
        if (legionnaires < 0 || archers < 0 || legionnaires + archers == 0)
        {
            return "Choisissez au moins un soldat.";
        }
        if (!Diplomacy.mayAssault(server, attacker.getID(), defender.getID()))
        {
            return "Impossible : aucune guerre ouverte contre cette colonie (ou préavis en cours).";
        }
        if (marching(attacker.getID(), defender.getID()) || WarAssaults.assaulting(attacker.getID(), defender.getID()))
        {
            return "Vos troupes sont déjà en campagne contre cette colonie.";
        }
        if (Garrison.available(server, attacker, Garrison.LEGIONNAIRE) < legionnaires
              || Garrison.available(server, attacker, Garrison.ARCHERE) < archers)
        {
            return "Pas assez de soldats dans la garnison.";
        }
        final List<Integer> legion = Garrison.depart(server, attacker, Garrison.LEGIONNAIRE, legionnaires);
        final List<Integer> bows = Garrison.depart(server, attacker, Garrison.ARCHERE, archers);
        MARCHES.add(new March(attacker.getID(), defender.getID(), legion, bows));
        Diplomacy.notifyMembers(server, attacker, "⚔ " + legion.size() + " légionnaire(s) et " + bows.size()
          + " archère(s) quittent la colonie pour marcher sur " + defender.getName() + ".");
        return null;
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        if (MARCHES.isEmpty())
        {
            return;
        }
        final MinecraftServer server = event.getServer();
        final ServerLevel level = server.overworld();
        final boolean check = ++ticks % CHECK_EVERY == 0;
        final Iterator<March> it = MARCHES.iterator();
        while (it.hasNext())
        {
            final March march = it.next();
            final IColony attacker = colony(server, march.attacker);
            final IColony defender = colony(server, march.defender);
            if (attacker == null || defender == null || !Diplomacy.mayAssault(server, march.attacker, march.defender))
            {
                it.remove();
                sendHome(level, march, attacker, "⚔ La guerre est finie : les troupes en marche rentrent aux tours.");
                continue;
            }
            if (march.path == null)
            {
                if (check)
                {
                    march.path = pathFor(server, march, defender);
                }
                continue;
            }
            march.progress = Math.min(march.path.length - 1, march.progress + Config.CARAVAN_BLOCKS_PER_SECOND.get() / 20.0);
            if (march.progress >= march.path.length - 1)
            {
                it.remove();
                arrive(level, march, attacker, defender);
                continue;
            }
            if (check)
            {
                showOrHide(level, march);
            }
            else
            {
                movePuppets(level, march);
            }
        }
    }

    /**
     * The road to follow, or null while it is still being built. Without any possible road, the troops go straight
     * to the enemy.
     */
    @Nullable
    private static BlockPos[] pathFor(final MinecraftServer server, final March march, final IColony defender)
    {
        final BlockPos[] road = RouteRegistry.get(server).pathBetween(march.attacker, march.defender);
        if (road != null && road.length > 1)
        {
            if (!march.announced)
            {
                Diplomacy.notifyMembers(server, colony(server, march.attacker), "⚔ La troupe s'engage sur la route de guerre ("
                  + road.length + " blocs).");
                march.announced = true;
            }
            return road;
        }
        if (WarRoad.building(march.attacker, march.defender))
        {
            if (!march.announced)
            {
                Diplomacy.notifyMembers(server, colony(server, march.attacker), "⚔ La troupe attend la fin du chantier de la route de guerre.");
                march.announced = true;
            }
            return null;
        }
        // No road and none on the way: straight to the enemy, the assault itself picks a spot around the colony
        return new BlockPos[] {defender.getCenter()};
    }

    private static void arrive(final ServerLevel level, final March march, final IColony attacker, final IColony defender)
    {
        clearPuppets(level, march);
        final BlockPos rally = march.path.length > 1 ? march.path[march.path.length - 1] : null;
        final String refusal = WarAssaults.launch(level, attacker, defender, null, march.legion, march.archers, rally, true);
        if (refusal != null)
        {
            sendHome(level, march, attacker, "⚔ Arrivées devant " + defender.getName() + ", les troupes ne peuvent pas attaquer : "
              + refusal + " Elles rentrent aux tours.");
            return;
        }
        Diplomacy.notifyMembers(level.getServer(), attacker, "⚔ Vos troupes donnent l'assaut sur " + defender.getName() + " !");
        Diplomacy.notifyMembers(level.getServer(), defender, "⚔ " + attacker.getName() + " donne l'assaut sur votre colonie !");
        Alliances.broadcast(level.getServer(), attacker, defender);
    }

    /**
     * Calls back the troops still on the road.
     *
     * @return a human-readable refusal, or null when they were called back.
     */
    @Nullable
    public static String recall(final MinecraftServer server, final IColony attacker, final int defender)
    {
        final Iterator<March> it = MARCHES.iterator();
        while (it.hasNext())
        {
            final March march = it.next();
            if (march.attacker == attacker.getID() && march.defender == defender)
            {
                it.remove();
                sendHome(server.overworld(), march, attacker, "⚔ Les troupes en marche sont rappelées.");
                return null;
            }
        }
        return "Aucune troupe en marche contre elle.";
    }

    private static void sendHome(final ServerLevel level, final March march, @Nullable final IColony attacker, final String line)
    {
        clearPuppets(level, march);
        if (attacker == null)
        {
            return;
        }
        int homeless = 0;
        for (final int troopLevel : march.legion)
        {
            homeless += Garrison.welcomeBack(level.getServer(), attacker, Garrison.LEGIONNAIRE, troopLevel) ? 0 : 1;
        }
        for (final int troopLevel : march.archers)
        {
            homeless += Garrison.welcomeBack(level.getServer(), attacker, Garrison.ARCHERE, troopLevel) ? 0 : 1;
        }
        Diplomacy.notifyMembers(level.getServer(), attacker, line + (homeless > 0 ? " " + homeless + " sans place libre dans les tours (perdus)." : ""));
        final IColony defender = colony(level.getServer(), march.defender);
        if (defender != null)
        {
            Alliances.broadcast(level.getServer(), attacker, defender);
        }
    }

    // ---- puppets: the march made visible, like the caravans ----

    private static void showOrHide(final ServerLevel level, final March march)
    {
        final BlockPos at = march.path[(int) march.progress];
        if (!playerNear(level, at) || !level.isLoaded(at))
        {
            clearPuppets(level, march);
            return;
        }
        march.puppets.removeIf(id -> {
            final Entity e = level.getEntity(id);
            return e == null || !e.isAlive();
        });
        while (march.puppets.size() < march.size())
        {
            final int slot = march.puppets.size();
            final boolean legion = slot < march.legion.size();
            final EntityType<? extends AssaultTroop> type = legion ? WarEntities.LEGIONNAIRE.get() : WarEntities.ARCHER.get();
            final AssaultTroop troop = type.create(level);
            if (troop == null)
            {
                break;
            }
            final int troopLevel = legion ? march.legion.get(slot) : march.archers.get(slot - march.legion.size());
            if (troopLevel > 0)
            {
                troop.setTroopLevel(troopLevel);
            }
            troop.setNoAi(true);
            troop.setSilent(true);
            troop.setInvulnerable(true);
            troop.addTag(NAME_TAG);
            final IColony attacker = colony(level.getServer(), march.attacker);
            if (attacker != null)
            {
                troop.setSenderColor(fr.kodistan.interregne.colony.ColonyColors.rgb(attacker));
            }
            if (attacker != null && slot == 0)
            {
                troop.setCustomName(Component.literal("⚔ Troupes de " + attacker.getName()).withColor(fr.kodistan.interregne.colony.ColonyColors.rgb(attacker)));
                troop.setCustomNameVisible(true);
            }
            place(troop, march, slot);
            level.addFreshEntity(troop);
            march.puppets.add(troop.getUUID());
        }
        movePuppets(level, march);
    }

    private static void movePuppets(final ServerLevel level, final March march)
    {
        for (int slot = 0; slot < march.puppets.size(); slot++)
        {
            final Entity troop = level.getEntity(march.puppets.get(slot));
            if (troop != null)
            {
                place(troop, march, slot);
            }
        }
    }

    /** In column, two abreast, each pair a block and a half behind the one before. */
    private static void place(final Entity troop, final March march, final int slot)
    {
        final double p = Math.max(0, march.progress - (slot / 2) * 1.5);
        final int index = (int) p;
        final BlockPos a = march.path[index];
        final BlockPos b = march.path[Math.min(index + 1, march.path.length - 1)];
        final double t = p - index;
        final double dx = b.getX() - a.getX();
        final double dz = b.getZ() - a.getZ();
        final double length = Math.max(1e-6, Math.sqrt(dx * dx + dz * dz));
        final double side = slot % 2 == 0 ? -0.6 : 0.6;
        final double x = a.getX() + 0.5 + dx * t - dz / length * side;
        final double z = a.getZ() + 0.5 + dz * t + dx / length * side;
        final float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        troop.setPos(x, a.getY() + 1.0, z);
        troop.setYRot(yaw);
        troop.setYHeadRot(yaw);
        troop.setYBodyRot(yaw);
        troop.setDeltaMovement(Vec3.ZERO);
        troop.fallDistance = 0;
    }

    private static void clearPuppets(final ServerLevel level, final March march)
    {
        for (final UUID id : march.puppets)
        {
            final Entity troop = level.getEntity(id);
            if (troop != null)
            {
                troop.discard();
            }
        }
        march.puppets.clear();
    }

    private static boolean playerNear(final ServerLevel level, final BlockPos at)
    {
        final double distance = Config.CARAVAN_SHOW_DISTANCE.get();
        for (final ServerPlayer player : level.players())
        {
            if (player.distanceToSqr(at.getX(), player.getY(), at.getZ()) < distance * distance)
            {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static IColony colony(final MinecraftServer server, final int id)
    {
        return IColonyManager.getInstance().getColonyByWorld(id, server.overworld());
    }

    @SubscribeEvent
    public static void onServerStopping(final ServerStoppingEvent event)
    {
        final ServerLevel level = event.getServer().overworld();
        for (final March march : new ArrayList<>(MARCHES))
        {
            sendHome(level, march, colony(event.getServer(), march.attacker), "⚔ Marche interrompue (arrêt du serveur) : les troupes rentrent aux tours.");
        }
        MARCHES.clear();
        ticks = 0;
    }
}
