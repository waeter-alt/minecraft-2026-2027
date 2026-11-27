package fr.kodistan.interregne.colony;

import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * A new player gets the build wand once, on first join: every hut, the town hall included, is placed through its
 * catalogue, and without it a survival player on a fresh server cannot found a colony at all.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class StarterKit
{
    private static final String GIVEN = "interregne_starter_kit";
    private static final ResourceLocation WAND = ResourceLocation.fromNamespaceAndPath("structurize", "sceptergold");

    private StarterKit()
    {
    }

    @SubscribeEvent
    public static void onLogin(final PlayerEvent.PlayerLoggedInEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        final CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GIVEN))
        {
            return;
        }
        final ItemStack wand = new ItemStack(BuiltInRegistries.ITEM.get(WAND));
        if (wand.is(Items.AIR))
        {
            return;
        }
        if (!player.getInventory().add(wand))
        {
            player.drop(wand, false);
        }
        persisted.putBoolean(GIVEN, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }
}
