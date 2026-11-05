package fr.kodistan.interregne.pvp;

import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.economy.CoinItem;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.politics.Influence;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Death in La Couronne Vacante: players keep armor, hotbar and inventory, but backpacks always fall with their contents,
 * and a player killed by another player also drops every Couronne carried and loses some Influence.
 * <p>
 * Kept items are lifted out of the inventory just before vanilla drops it, stashed in the dead player's
 * persistent data (so a logout on the death screen loses nothing), then put back in the same slots on respawn.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class DeathRule
{
    public static final TagKey<Item> DROPS_ON_DEATH = ItemTags.create(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "drops_on_death"));

    private static final String STASH = "interregne_death_stash";

    /**
     * Death summaries waiting for their player to respawn.
     */
    private static final Map<UUID, Component> SUMMARIES = new HashMap<>();

    private DeathRule()
    {
    }

    /**
     * Lowest priority: runs once nothing else will cancel the death (totems, other mods).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(final LivingDeathEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Config.DEATH_RULE.get())
        {
            return;
        }
        final ServerPlayer killer = killerOf(player, event.getSource());
        final boolean coinsDrop = killer != null && Config.PVP_DROPS_COINS.get();
        // With keepInventory on, vanilla drops nothing: drop the lost items ourselves
        final boolean keepInventory = player.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);

        final Inventory inventory = player.getInventory();
        final ListTag stash = new ListTag();
        final List<String> backpacks = new ArrayList<>();
        long coins = 0;
        int kept = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
        {
            final ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty())
            {
                continue;
            }
            final boolean backpack = stack.is(DROPS_ON_DEATH);
            if (backpack || coinsDrop && stack.getItem() instanceof CoinItem)
            {
                if (backpack)
                {
                    backpacks.add(stack.getHoverName().getString() + (stack.getCount() > 1 ? " ×" + stack.getCount() : ""));
                }
                else
                {
                    coins += Wallet.value(stack);
                }
                if (keepInventory)
                {
                    inventory.setItem(slot, ItemStack.EMPTY);
                    player.drop(stack, true, false);
                }
                continue;
            }
            if (!keepInventory && EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP))
            {
                // Curse of Vanishing: vanilla destroys it as usual
                continue;
            }
            final CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", slot);
            entry.put("Item", stack.save(player.registryAccess()));
            stash.add(entry);
            inventory.setItem(slot, ItemStack.EMPTY);
            kept++;
        }
        if (!stash.isEmpty())
        {
            player.getPersistentData().put(STASH, stash);
        }

        long influenceLost = 0;
        if (killer != null)
        {
            influenceLost = Influence.remove(player.server, player.getUUID(), Config.PVP_INFLUENCE_LOSS.get(), "tué par " + killer.getGameProfile().getName());
        }
        SUMMARIES.put(player.getUUID(), summary(player, killer, kept, backpacks, coins, influenceLost));
    }

    @SubscribeEvent
    public static void onClone(final PlayerEvent.Clone event)
    {
        if (event.isWasDeath() && event.getEntity() instanceof ServerPlayer player)
        {
            restore(event.getOriginal(), player, false);
        }
    }

    @SubscribeEvent
    public static void onRespawn(final PlayerEvent.PlayerRespawnEvent event)
    {
        final Component summary = SUMMARIES.remove(event.getEntity().getUUID());
        if (summary != null)
        {
            event.getEntity().sendSystemMessage(summary);
        }
    }

    /**
     * Safety net: a stash left on a living player (death cancelled after us, inventory full on respawn) is given back.
     */
    @SubscribeEvent
    public static void onPlayerTick(final PlayerTickEvent.Post event)
    {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0 && !player.isDeadOrDying()
              && player.getPersistentData().contains(STASH))
        {
            restore(player, player, true);
        }
    }

    /**
     * Put the stashed items back in their slots.
     *
     * @param drop whether items that no longer fit are dropped at the player's feet, else they stay stashed for later.
     */
    private static void restore(final Player from, final ServerPlayer to, final boolean drop)
    {
        final CompoundTag data = from.getPersistentData();
        if (!data.contains(STASH, Tag.TAG_LIST))
        {
            return;
        }
        final ListTag stash = data.getList(STASH, Tag.TAG_COMPOUND);
        data.remove(STASH);
        final Inventory inventory = to.getInventory();
        final ListTag leftover = new ListTag();
        for (int i = 0; i < stash.size(); i++)
        {
            final CompoundTag entry = stash.getCompound(i);
            final int slot = entry.getInt("Slot");
            final ItemStack stack = ItemStack.parseOptional(to.registryAccess(), entry.getCompound("Item"));
            if (stack.isEmpty())
            {
                continue;
            }
            if (slot >= 0 && slot < inventory.getContainerSize() && inventory.getItem(slot).isEmpty())
            {
                inventory.setItem(slot, stack);
            }
            else if (!inventory.add(stack))
            {
                if (drop)
                {
                    to.drop(stack, false);
                }
                else
                {
                    entry.put("Item", stack.save(to.registryAccess()));
                    leftover.add(entry);
                }
            }
        }
        if (!leftover.isEmpty())
        {
            to.getPersistentData().put(STASH, leftover);
        }
    }

    /**
     * The player behind this death, directly or through a projectile, or the last player who hurt the victim
     * (knocked into lava, off a cliff).
     */
    @Nullable
    private static ServerPlayer killerOf(final ServerPlayer victim, final DamageSource source)
    {
        if (source.getEntity() instanceof ServerPlayer killer && killer != victim)
        {
            return killer;
        }
        final LivingEntity credit = victim.getKillCredit();
        return credit instanceof ServerPlayer killer && killer != victim ? killer : null;
    }

    private static Component summary(final ServerPlayer player, @Nullable final ServerPlayer killer, final int kept, final List<String> backpacks,
      final long coins, final long influenceLost)
    {
        final MutableComponent text = Component.literal("☠ Bilan de ta mort" + (killer == null ? "" : " (tué par " + killer.getGameProfile().getName() + ")"))
                                        .withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD);
        text.append(line("Conservé : armure, barre d'action et inventaire (" + kept + (kept > 1 ? " piles)" : " pile)"), ChatFormatting.GREEN));
        boolean lost = false;
        for (final String backpack : backpacks)
        {
            text.append(line("Perdu : " + backpack + " — tombé au sol avec son contenu", ChatFormatting.RED));
            lost = true;
        }
        if (coins > 0)
        {
            text.append(line("Perdu : " + Wallet.couronnes(coins, "tombée") + " au sol", ChatFormatting.RED));
            lost = true;
        }
        if (influenceLost > 0)
        {
            text.append(line("Perdu : " + influenceLost + " d'Influence (il t'en reste " + Influence.get(player.server, player.getUUID()) + ")", ChatFormatting.RED));
            lost = true;
        }
        if (!lost)
        {
            text.append(line("Rien d'autre n'a été perdu.", ChatFormatting.GREEN));
        }
        return text;
    }

    private static Component line(final String content, final ChatFormatting color)
    {
        return Component.literal("\n  " + content).withStyle(style -> style.withBold(false).withColor(color));
    }
}
