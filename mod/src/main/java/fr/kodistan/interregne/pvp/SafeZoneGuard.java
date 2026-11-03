package fr.kodistan.interregne.pvp;

import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enforces safe zones: no player-vs-player damage when either side stands inside one (melee, arrows, potions, TNT...),
 * and a discreet action-bar notice on the way in and out. PvP stays open everywhere else.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class SafeZoneGuard
{
    private static final int CHECK_INTERVAL = 10;

    /**
     * Name of the zone each player was last seen in.
     */
    private static final Map<UUID, String> CURRENT = new HashMap<>();

    private SafeZoneGuard()
    {
    }

    @SubscribeEvent
    public static void onIncomingDamage(final LivingIncomingDamageEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer victim) || !(event.getSource().getEntity() instanceof Player attacker)
              || attacker == victim || !Config.SAFE_ZONES.get())
        {
            return;
        }
        if (zoneOf(victim) != null || zoneOf(attacker) != null)
        {
            event.setCanceled(true);
            if (attacker instanceof ServerPlayer serverAttacker)
            {
                serverAttacker.displayClientMessage(Component.literal("Zone sûre : pas de combat entre joueurs ici").withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(final PlayerTickEvent.Post event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % CHECK_INTERVAL != 0)
        {
            return;
        }
        final SafeZones.Zone zone = Config.SAFE_ZONES.get() ? zoneOf(player) : null;
        final String name = zone == null ? null : zone.name();
        final String previous = CURRENT.get(player.getUUID());
        if (name == null ? previous == null : name.equals(previous))
        {
            return;
        }
        if (name == null)
        {
            CURRENT.remove(player.getUUID());
        }
        else
        {
            CURRENT.put(player.getUUID(), name);
        }
        if (Config.SAFE_ZONE_MESSAGES.get())
        {
            player.displayClientMessage(name == null
                                          ? Component.literal("Vous quittez la zone sûre").withStyle(ChatFormatting.GRAY)
                                          : Component.literal("Zone sûre : " + name).withStyle(ChatFormatting.GREEN), true);
        }
    }

    @SubscribeEvent
    public static void onLogout(final PlayerEvent.PlayerLoggedOutEvent event)
    {
        CURRENT.remove(event.getEntity().getUUID());
    }

    @Nullable
    private static SafeZones.Zone zoneOf(final Player player)
    {
        if (player.getServer() == null)
        {
            return null;
        }
        return SafeZones.get(player.getServer()).at(player.level().dimension(), player.blockPosition());
    }
}
