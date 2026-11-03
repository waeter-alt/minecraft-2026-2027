package fr.kodistan.interregne.region;

import fr.kodistan.interregne.Config;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.EnumMap;
import java.util.Map;

/**
 * The Empire's crate stock behind every Marché impérial. Buying empties it and raises prices, selling fills it and
 * lowers them; between the two prices lies the Empire's margin. Left alone, each stock drifts back to its base.
 */
public final class ImperialMarket extends SavedData
{
    private static final String NAME = "interregne_market";

    private final Map<Crate, Integer> stock = new EnumMap<>(Crate.class);
    private long lastRestock = -1;

    public static ImperialMarket get(final MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(ImperialMarket::new, ImperialMarket::load, null), NAME);
    }

    public int stock(final Crate crate)
    {
        return stock.getOrDefault(crate, Config.MARKET_BASE_STOCK.get());
    }

    public void setStock(final Crate crate, final int amount)
    {
        stock.put(crate, Math.max(0, amount));
        setDirty();
    }

    /**
     * Price multiplier at this stock: 1 at the base stock, higher when scarce, lower when plentiful, within floor and ceiling.
     */
    public static double factor(final int stock)
    {
        final double base = Config.MARKET_BASE_STOCK.get();
        final double raw = 1.0 + Config.MARKET_ELASTICITY.get() * (base - stock) / base;
        return Math.clamp(raw, Config.MARKET_MIN_FACTOR.get(), Config.MARKET_MAX_FACTOR.get());
    }

    /**
     * What a player pays for the next crate.
     */
    public int buyPrice(final Crate crate)
    {
        return buyPriceAt(stock(crate));
    }

    /**
     * What a player receives for the next crate.
     */
    public int sellPrice(final Crate crate)
    {
        return sellPriceAt(stock(crate));
    }

    /**
     * The buy price is that of the crate leaving the stock, so it never falls under the price the next seller gets.
     */
    public static int buyPriceAt(final int stock)
    {
        return (int) Math.max(1, Math.round(Config.MARKET_BUY_PRICE.get() * factor(stock - 1)));
    }

    public static int sellPriceAt(final int stock)
    {
        return (int) Math.max(1, Math.round(Config.MARKET_SELL_PRICE.get() * factor(stock)));
    }

    /**
     * Total price of buying this many crates in a row, each one raising the next.
     */
    public long buyTotal(final Crate crate, final int amount)
    {
        long total = 0;
        final int current = stock(crate);
        for (int i = 0; i < amount; i++)
        {
            total += buyPriceAt(current - i);
        }
        return total;
    }

    /**
     * Total received for selling this many crates in a row, each one lowering the next.
     */
    public long sellTotal(final Crate crate, final int amount)
    {
        long total = 0;
        final int current = stock(crate);
        for (int i = 0; i < amount; i++)
        {
            total += sellPriceAt(current + i);
        }
        return total;
    }

    /**
     * Move each stock one crate toward its base every configured period.
     */
    public void restock(final long gameTime)
    {
        final int minutes = Config.MARKET_RESTOCK_MINUTES.get();
        if (minutes <= 0)
        {
            return;
        }
        if (lastRestock < 0 || gameTime < lastRestock)
        {
            lastRestock = gameTime;
            setDirty();
            return;
        }
        final long period = minutes * 1200L;
        if (gameTime - lastRestock < period)
        {
            return;
        }
        lastRestock = gameTime;
        final int base = Config.MARKET_BASE_STOCK.get();
        for (final Crate crate : Crate.values())
        {
            final int current = stock(crate);
            if (current != base)
            {
                stock.put(crate, current + Integer.signum(base - current));
            }
        }
        setDirty();
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider provider)
    {
        final CompoundTag stockTag = new CompoundTag();
        stock.forEach((crate, amount) -> stockTag.putInt(crate.id(), amount));
        tag.put("stock", stockTag);
        tag.putLong("lastRestock", lastRestock);
        return tag;
    }

    private static ImperialMarket load(final CompoundTag tag, final HolderLookup.Provider provider)
    {
        final ImperialMarket market = new ImperialMarket();
        final CompoundTag stockTag = tag.getCompound("stock");
        for (final String key : stockTag.getAllKeys())
        {
            final Crate crate = Crate.byId(key);
            if (crate != null)
            {
                market.stock.put(crate, stockTag.getInt(key));
            }
        }
        market.lastRestock = tag.getLong("lastRestock");
        return market;
    }
}
