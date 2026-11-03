package fr.kodistan.interregne.economy;

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
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wealth weighs: carrying a fortune slows you down, and a dragon's hoard makes you glow for everyone to see.
 * Big money belongs in the Imperial Bank.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class Opulence
{
    private static final int CHECK_INTERVAL = 20;
    private static final int EFFECT_TICKS   = CHECK_INTERVAL * 2 + 5;

    /**
     * Last weight level shown to each player, to announce changes only once.
     */
    private static final Map<UUID, Integer> LAST_LEVEL = new HashMap<>();

    private Opulence()
    {
    }

    @SubscribeEvent
    public static void onPlayerTick(final PlayerTickEvent.Post event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % CHECK_INTERVAL != 0 || player.isCreative() || player.isSpectator())
        {
            return;
        }
        final long carried = Wallet.value(new PlayerMainInvWrapper(player.getInventory()));
        final int level = carried >= Config.DRAGON_PURSE.get() ? 2 : carried >= Config.HEAVY_PURSE.get() ? 1 : 0;

        if (level > 0)
        {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_TICKS, level - 1, true, false, true));
        }
        if (level > 1)
        {
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, EFFECT_TICKS, 0, true, false, true));
        }

        final int previous = LAST_LEVEL.getOrDefault(player.getUUID(), 0);
        if (level != previous)
        {
            LAST_LEVEL.put(player.getUUID(), level);
            if (level > previous)
            {
                player.displayClientMessage(Component.literal(level == 2
                                                                ? "Un trésor de dragon sur le dos : tu brilles aux yeux de tous !"
                                                                : "Ta bourse pèse lourd : tu ralentis.").withStyle(ChatFormatting.GOLD), true);
            }
        }
    }
}
