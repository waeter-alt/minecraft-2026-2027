package fr.kodistan.interregne.colony;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minecolonies.api.colony.workorders.IServerWorkOrder;
import com.minecolonies.core.colony.workorders.WorkOrderDecoration;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.network.chat.Component;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The catalogue's French name of a decoration (assets/interregne/deco_icons.json), for chat messages about its site:
 * « Chantier terminé : Champ d'herbes marines » rather than the blueprint's file name.
 */
public final class DecoNames
{
    private static JsonObject names;

    private DecoNames()
    {
    }

    /**
     * What to call this work order in a message.
     */
    public static Component of(final IServerWorkOrder order)
    {
        if (order instanceof WorkOrderDecoration)
        {
            try
            {
                final String name = lookup(order.getStructurePack(), order.getStructurePath());
                if (name != null)
                {
                    return Component.literal(name);
                }
            }
            catch (final RuntimeException e)
            {
                // Fall back on MineColonies' own name
            }
        }
        return order.getDisplayName();
    }

    private static String lookup(final String pack, final String path)
    {
        final JsonObject style = names().getAsJsonObject(pack);
        if (style == null || path == null)
        {
            return null;
        }
        String key = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        key = key.startsWith("/") ? key.substring(1) : key;
        key = key.endsWith(".blueprint") ? key.substring(0, key.length() - ".blueprint".length()) : key;
        key = key.replaceAll("\\d+$", "");
        final JsonObject pieces = style.getAsJsonObject("pieces");
        return pieces.has(key) ? pieces.getAsJsonObject(key).get("name").getAsString() : null;
    }

    private static synchronized JsonObject names()
    {
        if (names == null)
        {
            names = new JsonObject();
            try (Reader reader = new InputStreamReader(DecoNames.class.getResourceAsStream("/assets/" + InterregneMod.MODID + "/deco_icons.json"), StandardCharsets.UTF_8))
            {
                names = JsonParser.parseReader(reader).getAsJsonObject();
            }
            catch (final Exception e)
            {
                InterregneMod.LOGGER.warn("L'Interregne: decoration names unreadable", e);
            }
        }
        return names;
    }
}
