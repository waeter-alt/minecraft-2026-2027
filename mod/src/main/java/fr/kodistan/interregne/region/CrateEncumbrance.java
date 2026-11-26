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

    /**
     * The Slowness level (0 for none) that carrying this many crates gives: nothing up to the free load, then one level
     * more every {@code cratesPerSlowLevel} crates, never above {@code cratesSlowMax}.
     */
    public static int slowLevel(final int carried)
    {
        final int excess = carried - Config.CRATES_SLOWNESS_1.get();
        if (excess <= 0)
        {
            return 0;
        }
        return Math.min(Config.CRATES_SLOW_MAX.get(), 1 + (excess - 1) / Config.CRATES_PER_SLOW_LEVEL.get());
    }

    @SubscribeEvent
    public static void onPlayerTick(final PlayerTickEvent.Post event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % CHECK_INTERVAL != 0 || player.isCreative() || player.isSpectator())
        {
            return;
        }
        final int carried = CrateStores.countAll(new PlayerInvWrapper(player.getInventory()));
        final int level = slowLevel(carried);
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
                player.displayClientMessage(Component.literal(level >= 2
                                                                ? "Tu croules sous les caisses : tu avances très lentement."
                                                                : "Ces caisses pèsent lourd : tu ralentis.").withStyle(ChatFormatting.GOLD), true);
            }
        }
    }
}
