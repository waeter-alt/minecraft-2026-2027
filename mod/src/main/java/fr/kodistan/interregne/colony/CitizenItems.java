package fr.kodistan.interregne.colony;

import fr.kodistan.interregne.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Which items a citizen may carry. In 2025 a modded class-mod armor given to (or picked up by) a MineColonies citizen
 * froze the server for hours, and a rule announced in chat cannot protect players who are offline: only code can.
 */
public final class CitizenItems
{
    private static final List<String> FALLBACK = List.of("minecraft", "minecolonies", "structurize", "domum_ornamentum", "interregne");

    private CitizenItems()
    {
    }

    public static boolean allowed(final ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return true;
        }
        final List<? extends String> namespaces;
        try
        {
            if (!Config.CITIZEN_ITEM_ALLOWLIST.get())
            {
                return true;
            }
            namespaces = Config.CITIZEN_ITEM_NAMESPACES.get();
        }
        catch (final IllegalStateException e)
        {
            // Config not loaded yet: the safe default, never open
            return FALLBACK.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
        }
        return namespaces.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
    }
}
