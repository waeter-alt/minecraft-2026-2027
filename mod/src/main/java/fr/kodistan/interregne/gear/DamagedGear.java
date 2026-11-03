package fr.kodistan.interregne.gear;

import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * "Red" durability: players' gear never breaks. It stops at 1 durability left and becomes abîmé (damaged),
 * hitting and protecting less, until repaired. The state is derived from the damage value, never stored,
 * so any repair (anvil, Mending, grindstone, another mod) clears it.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class DamagedGear
{
    public static final String DAMAGED_TOOLTIP = "Abîmé — à réparer à la forge";

    private DamagedGear()
    {
    }

    /**
     * Whether this stack sits at its last durability point.
     */
    public static boolean isDamaged(final ItemStack stack)
    {
        return !stack.isEmpty() && stack.isDamageableItem() && stack.getMaxDamage() > 1 && stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    /**
     * Whether a stack about to break in this entity's hands must be saved instead.
     */
    public static boolean keepsFromBreaking(@Nullable final LivingEntity entity, final ItemStack stack)
    {
        return entity instanceof Player && stack.getMaxDamage() > 1 && config(Config.UNBREAKABLE_PLAYER_GEAR, false);
    }

    /**
     * Leave the stack at 1 durability and warn its owner the first time.
     */
    public static void wearOut(final ItemStack stack, @Nullable final LivingEntity entity, final boolean wasDamaged)
    {
        stack.setDamageValue(stack.getMaxDamage() - 1);
        if (!wasDamaged && entity instanceof ServerPlayer player)
        {
            player.displayClientMessage(Component.literal(stack.getHoverName().getString() + " : abîmé — à réparer à la forge").withStyle(ChatFormatting.RED), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.6F, 0.6F);
        }
    }

    @SubscribeEvent
    public static void onAttributes(final ItemAttributeModifierEvent event)
    {
        if (!isDamaged(event.getItemStack()))
        {
            return;
        }
        final double keep = 1.0 - config(Config.DAMAGED_STAT_PENALTY, 0.5);
        final List<ItemAttributeModifiers.Entry> entries = new ArrayList<>(event.getModifiers());
        for (final ItemAttributeModifiers.Entry entry : entries)
        {
            final AttributeModifier modifier = entry.modifier();
            if (isWeakened(entry.attribute()) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE && modifier.amount() > 0)
            {
                event.replaceModifier(entry.attribute(), new AttributeModifier(modifier.id(), modifier.amount() * keep, modifier.operation()), entry.slot());
            }
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(final PlayerEvent.BreakSpeed event)
    {
        if (isDamaged(event.getEntity().getMainHandItem()))
        {
            event.setNewSpeed(event.getNewSpeed() * (float) (1.0 - config(Config.DAMAGED_MINING_PENALTY, 0.5)));
        }
    }

    private static boolean isWeakened(final Holder<Attribute> attribute)
    {
        return attribute.value() == Attributes.ATTACK_DAMAGE.value()
                 || attribute.value() == Attributes.ARMOR.value()
                 || attribute.value() == Attributes.ARMOR_TOUGHNESS.value();
    }

    /**
     * A server config value, or the fallback while no world is loaded (tooltips in the main menu).
     */
    private static <T> T config(final ModConfigSpec.ConfigValue<T> value, final T fallback)
    {
        try
        {
            return value.get();
        }
        catch (final IllegalStateException e)
        {
            return fallback;
        }
    }
}
