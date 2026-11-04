package fr.kodistan.interregne;

import net.neoforged.fml.config.ConfigTracker;

/**
 * Our settings are server config, which only loads when a world opens. Tests read their default values instead.
 */
public final class Defaults
{
    private static boolean loaded;

    private Defaults()
    {
    }

    public static synchronized void load()
    {
        if (!loaded)
        {
            ConfigTracker.INSTANCE.loadDefaultServerConfigs();
            loaded = true;
        }
    }
}
