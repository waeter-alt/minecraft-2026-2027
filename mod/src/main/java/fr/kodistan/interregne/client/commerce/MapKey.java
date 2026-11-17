package fr.kodistan.interregne.client.commerce;

import com.mojang.blaze3d.platform.InputConstants;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The key that opens the kingdom map from the game (M by default; the player can rebind it in the controls).
 */
public final class MapKey
{
    public static final KeyMapping MAPPING = new KeyMapping("key.interregne.carte", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.interregne");

    private MapKey()
    {
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
                // Only from the game itself: a text field or another window keeps its keys
                if (minecraft.screen == null && minecraft.level != null)
                {
                    minecraft.setScreen(new KingdomMapScreen(null));
                }
            }
        }
    }
}
