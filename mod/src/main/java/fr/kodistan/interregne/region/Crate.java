package fr.kodistan.interregne.region;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.text.Normalizer;
import java.util.Locale;

/**
 * The five regional building materials, Catan style: each colony produces one, trades for the others.
 */
public enum Crate
{
    BOIS("bois", "Caisse de bois", "du Bois"),
    BRIQUE("brique", "Caisse de briques", "de la Brique"),
    BLE("ble", "Caisse de blé", "du Blé"),
    PIERRE("pierre", "Caisse de pierre", "de la Pierre"),
    MINERAI("minerai", "Caisse de minerai", "du Minerai");

    private final String id;
    private final String displayName;
    private final String regionSuffix;

    Crate(final String id, final String displayName, final String regionSuffix)
    {
        this.id = id;
        this.displayName = displayName;
        this.regionSuffix = regionSuffix;
    }

    /**
     * Short key, used in configs, commands and saved data.
     */
    public String id()
    {
        return id;
    }

    public String displayName()
    {
        return displayName;
    }

    /**
     * "la Région de la Pierre"
     */
    public String regionName()
    {
        return "la Région " + regionSuffix;
    }

    public Item item()
    {
        return Crates.item(this);
    }

    public ItemStack stack(final int count)
    {
        return new ItemStack(item(), count);
    }

    /**
     * "1 caisse de bois", "3 caisses de briques"
     */
    public String count(final int amount)
    {
        return amount + " " + (amount > 1 ? pluralName() : displayName.toLowerCase(Locale.ROOT));
    }

    /**
     * "caisses de briques"
     */
    public String pluralName()
    {
        return displayName.toLowerCase(Locale.ROOT).replaceFirst("^caisse", "caisses");
    }

    /**
     * Parse a key, forgiving accents, case and plurals ("blé", "Briques").
     */
    @Nullable
    public static Crate byId(final String key)
    {
        final String plain = Normalizer.normalize(key.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        for (final Crate crate : values())
        {
            if (plain.equals(crate.id) || plain.equals(crate.id + "s"))
            {
                return crate;
            }
        }
        return null;
    }

    @Nullable
    public static Crate of(final ItemStack stack)
    {
        return stack.getItem() instanceof CrateItem crate ? crate.crate() : null;
    }
}
