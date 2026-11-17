package fr.kodistan.interregne.routes;

import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A player on a road goes faster the better the road (speed, jump, step height, softer falls, a little regeneration
 * and resistance on the best ones), and loses it all on leaving it. Server side, checked every half second with one
 * map lookup per player; the modifiers are transient, so they never end up saved on the player.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class RoadEffects
{
    private static final int EVERY_TICKS = 10;
    /** Potions are given this long and topped up before they run out, so they fade soon after leaving the road. */
    private static final int POTION_TICKS = 100;
    private static final int POTION_REFRESH = 40;

    private static final ResourceLocation SPEED = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "road_speed");
    private static final ResourceLocation JUMP = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "road_jump");
    private static final ResourceLocation STEP = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "road_step");
    private static final ResourceLocation FALL = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "road_safe_fall");

    /** The road level each player last got the effects of (absent: none). */
    private static final Map<UUID, Integer> ON_ROAD = new HashMap<>();

    private RoadEffects()
    {
    }

    @SubscribeEvent
    public static void onPlayerTick(final PlayerTickEvent.Post event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % EVERY_TICKS != 0)
        {
            return;
        }
        final int level = Config.ROAD_EFFECTS.get() && player.level().dimension() == Level.OVERWORLD && !player.isSpectator()
                            ? RouteRegistry.get(player.server).levelAt(player.blockPosition()) : 0;
        final Integer had = ON_ROAD.get(player.getUUID());
        if (level == 0)
        {
            if (had != null)
            {
                clear(player);
            }
            return;
        }
        if (had == null || had != level)
        {
            apply(player, level);
            ON_ROAD.put(player.getUUID(), level);
        }
        if (Config.ROAD_POTIONS.get())
        {
            potion(player, MobEffects.REGENERATION, level >= 4);
            potion(player, MobEffects.DAMAGE_RESISTANCE, level >= 5);
        }
    }

    private static void apply(final ServerPlayer player, final int level)
    {
        modifier(player, Attributes.MOVEMENT_SPEED, SPEED, RoadLevels.perLevel(Config.ROAD_SPEED.get(), level), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(player, Attributes.JUMP_STRENGTH, JUMP, RoadLevels.perLevel(Config.ROAD_JUMP.get(), level), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(player, Attributes.STEP_HEIGHT, STEP, RoadLevels.perLevel(Config.ROAD_STEP.get(), level), AttributeModifier.Operation.ADD_VALUE);
        modifier(player, Attributes.SAFE_FALL_DISTANCE, FALL, RoadLevels.perLevel(Config.ROAD_SAFE_FALL.get(), level), AttributeModifier.Operation.ADD_VALUE);
    }

    private static void modifier(final ServerPlayer player, final Holder<Attribute> attribute, final ResourceLocation id, final double amount,
      final AttributeModifier.Operation operation)
    {
        final AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null)
        {
            return;
        }
        instance.removeModifier(id);
        if (amount != 0)
        {
            instance.addTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    /**
     * Ours are ambient and never longer than {@link #POTION_TICKS}: a stronger or longer effect from elsewhere is left
     * alone, both when topping up and when taking it back.
     */
    private static void potion(final ServerPlayer player, final Holder<MobEffect> effect, final boolean wanted)
    {
        final MobEffectInstance current = player.getEffect(effect);
        final boolean ours = current != null && current.isAmbient() && current.getAmplifier() == 0 && !current.isInfiniteDuration()
                               && current.getDuration() <= POTION_TICKS;
        if (!wanted)
        {
            if (ours)
            {
                player.removeEffect(effect);
            }
            return;
        }
        if (current == null || ours && current.getDuration() <= POTION_REFRESH)
        {
            player.addEffect(new MobEffectInstance(effect, POTION_TICKS, 0, true, false, true));
        }
    }

    private static void clear(final ServerPlayer player)
    {
        ON_ROAD.remove(player.getUUID());
        for (final ResourceLocation id : new ResourceLocation[] {SPEED, JUMP, STEP, FALL})
        {
            remove(player, id);
        }
        potion(player, MobEffects.REGENERATION, false);
        potion(player, MobEffects.DAMAGE_RESISTANCE, false);
    }

    private static void remove(final ServerPlayer player, final ResourceLocation id)
    {
        for (final Holder<Attribute> attribute : java.util.List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH, Attributes.STEP_HEIGHT,
          Attributes.SAFE_FALL_DISTANCE))
        {
            final AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null)
            {
                instance.removeModifier(id);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(final PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            clear(player);
        }
    }

    /** The new body after death or the End starts without our modifiers: only forget the old one. */
    @SubscribeEvent
    public static void onRespawn(final PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            clear(player);
        }
    }

    @SubscribeEvent
    public static void onDimension(final PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            clear(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(final ServerStoppingEvent event)
    {
        ON_ROAD.clear();
    }
}
