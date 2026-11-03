package fr.kodistan.interregne.region;

import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.items.wrapper.PlayerInvWrapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Crates are heavy: a few slow you down, a cartload slows you a lot. Caravans and warehouses beat carrying them.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class CrateEncumbrance
{
    private static final int CHECK_INTERVAL = 20;
    private static final int EFFECT_TICKS   = CHECK_INTERVAL * 2 + 5;

    /**
     * Last load level shown to each player, to announce changes only once.
     */
    private static final Map<UUID, Integer> LAST_LEVEL = new HashMap<>();

    private CrateEncumbrance()
    {
    }

    @SubscribeEvent
    public static void onPlayerTick(final PlayerTickEvent.Post event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % CHECK_INTERVAL != 0 || player.isCreative() || player.isSpectator())
        {
            return;
        }
        final int carried = CrateStores.countAll(new PlayerInvWrapper(player.getInventory()));
        final int level = carried > Config.CRATES_SLOWNESS_2.get() ? 2 : carried > Config.CRATES_SLOWNESS_1.get() ? 1 : 0;
        if (level > 0)
        {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_TICKS, level - 1, true, false, true));
        }

        final int previous = LAST_LEVEL.getOrDefault(player.getUUID(), 0);
        if (level != previous)
        {
            LAST_LEVEL.put(player.getUUID(), level);
            if (level > previous)
            {
                player.displayClientMessage(Component.literal(level == 2
                                                                ? "Tu croules sous les caisses : tu avances très lentement."
                                                                : "Ces caisses pèsent lourd : tu ralentis.").withStyle(ChatFormatting.GOLD), true);
            }
        }
    }
}
