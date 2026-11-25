package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.mojang.blaze3d.platform.InputConstants;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The key that opens the player's colony hub on its Diplomacy tab from anywhere (K by default): an attacker far from
 * home can give up a war, a besieged defender can capitulate, without walking back to a building of their colony.
 */
public final class DiplomacyKey
{
    public static final KeyMapping MAPPING = new KeyMapping("key.interregne.diplomatie", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.interregne");

    private DiplomacyKey()
    {
    }

    /**
     * The colony the key opens, also the one the war indicator watches: the owned colony first, else the one the
     * player stands in when they belong to it.
     */
    @Nullable
    public static IColony playerColony(final Minecraft minecraft)
    {
        if (minecraft.level == null || minecraft.player == null)
        {
            return null;
        }
        final IColony owned = IColonyManager.getInstance().getIColonyByOwner(minecraft.level, minecraft.player);
        if (owned != null)
        {
            return owned;
        }
        final IColonyView here = IColonyManager.getInstance().getColonyView(minecraft.level, minecraft.player.blockPosition());
        return here != null && here.getPermissions().isColonyMember(minecraft.player) ? here : null;
    }

    @EventBusSubscriber(modid = InterregneMod.MODID, value = Dist.CLIENT)
    public static final class Register
    {
        @SubscribeEvent
        public static void onKeys(final RegisterKeyMappingsEvent event)
        {
            event.register(MAPPING);
        }
    }

    @EventBusSubscriber(modid = InterregneMod.MODID, value = Dist.CLIENT)
    public static final class Tick
    {
        @SubscribeEvent
        public static void onTick(final ClientTickEvent.Post event)
        {
            final Minecraft minecraft = Minecraft.getInstance();
            while (MAPPING.consumeClick())
            {
                if (minecraft.screen == null && minecraft.level != null && minecraft.player != null)
                {
                    open(minecraft);
                }
            }
        }

        private static void open(final Minecraft minecraft)
        {
            final IColony colony = playerColony(minecraft);
            if (colony instanceof IColonyView view && view.getTownHall() != null)
            {
                ColonyHubScreen.openOn(view.getTownHall(), ColonyHubScreen.Page.DIPLOMACY);
                return;
            }
            minecraft.player.displayClientMessage(Component.literal(colony == null
              ? "Diplomatie : vous n'avez pas de colonie (ou elle n'est pas encore chargée)."
              : "Diplomatie : votre colonie n'a pas encore d'hôtel de ville."), true);
        }
    }
}
