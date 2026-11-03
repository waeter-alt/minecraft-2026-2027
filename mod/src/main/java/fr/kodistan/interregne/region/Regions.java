package fr.kodistan.interregne.region;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.network.RegionActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gives every new colony its regional resource, the one the fewest colonies produce, and runs the Comptoirs.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class Regions
{
    private static final int ASSIGN_INTERVAL     = 100;
    private static final int PRODUCTION_INTERVAL = 20;

    private Regions()
    {
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        final MinecraftServer server = event.getServer();
        final int tick = server.getTickCount();
        if (tick % ASSIGN_INTERVAL == 0)
        {
            assignNewColonies(server);
        }
        if (tick % PRODUCTION_INTERVAL == 0)
        {
            runComptoirs(server);
            ImperialMarket.get(server).restock(server.overworld().getGameTime());
        }
    }

    /**
     * A click in a Comptoir or Marché window.
     */
    public static void onAction(final RegionActionPayload payload, final IPayloadContext context)
    {
        if (!(context.player() instanceof ServerPlayer player))
        {
            return;
        }
        if (payload.action() == RegionActionPayload.COMPTOIR_UPGRADE || payload.action() == RegionActionPayload.COMPTOIR_TAKE)
        {
            ComptoirBlock.onAction(payload, player);
        }
        else
        {
            MarketBlock.onAction(payload, player);
        }
    }

    /**
     * Ticks one crate takes at a Comptoir of this level.
     */
    public static int ticksPerCrate(final int level)
    {
        return secondsPerCrate(level) * 20;
    }

    public static int secondsPerCrate(final int level)
    {
        final int[] seconds = parseInts(Config.COMPTOIR_SECONDS.get(), new int[] {300, 240, 180, 120, 60});
        return Math.max(1, seconds[Math.clamp(level - 1, 0, seconds.length - 1)]);
    }

    /**
     * Couronnes to bring a Comptoir to this level (2 to 5).
     */
    public static long upgradeCost(final int targetLevel)
    {
        final int[] costs = parseInts(Config.COMPTOIR_UPGRADE_COSTS.get(), new int[] {200, 400, 800, 1600});
        return Math.max(0, costs[Math.clamp(targetLevel - 2, 0, costs.length - 1)]);
    }

    /**
     * Give a colony its region and announce it to the whole server.
     */
    public static void assign(final MinecraftServer server, final IColony colony, final Crate crate)
    {
        RegionData.get(server).setRegion(colony, crate);
        server.getPlayerList().broadcastSystemMessage(Component.literal(colony.getName() + " devient " + crate.regionName())
                                                        .withStyle(ChatFormatting.GOLD), false);
        InterregneMod.LOGGER.info("Region: colony {} ({}) produces {}", colony.getID(), colony.getName(), crate.id());
    }

    private static void assignNewColonies(final MinecraftServer server)
    {
        final RegionData data = RegionData.get(server);
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            if (!data.hasRegion(colony))
            {
                assign(server, colony, pick(data.claims(), preferred(colony)));
            }
        }
    }

    /**
     * The least claimed resource; among ties, the one suiting the town hall's biome, else one at random.
     */
    private static Crate pick(final Map<Crate, Integer> claims, @Nullable final Crate preferred)
    {
        final int fewest = claims.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        final List<Crate> candidates = new ArrayList<>();
        claims.forEach((crate, count) -> {
            if (count == fewest)
            {
                candidates.add(crate);
            }
        });
        if (preferred != null && candidates.contains(preferred))
        {
            return preferred;
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    /**
     * The resource the land around the town hall suggests.
     */
    @Nullable
    private static Crate preferred(final IColony colony)
    {
        final Level level = colony.getWorld();
        if (level == null)
        {
            return null;
        }
        final IBuilding townHall = colony.getBuildingManager().getTownHall();
        final BlockPos pos = townHall != null ? townHall.getPosition() : colony.getCenter();
        final Holder<Biome> biome = level.getBiome(pos);
        if (biome.is(Tags.Biomes.IS_BADLANDS) || biome.is(Tags.Biomes.IS_CAVE) || biome.is(Tags.Biomes.IS_UNDERGROUND) || biome.is(Tags.Biomes.IS_DEEP_OCEAN))
        {
            return Crate.MINERAI;
        }
        if (biome.is(Tags.Biomes.IS_MOUNTAIN) || biome.is(Tags.Biomes.IS_STONY_SHORES) || biome.is(Tags.Biomes.IS_WINDSWEPT) || biome.is(BiomeTags.IS_HILL))
        {
            return Crate.PIERRE;
        }
        if (biome.is(Tags.Biomes.IS_RIVER) || biome.is(Tags.Biomes.IS_BEACH) || biome.is(Tags.Biomes.IS_SWAMP))
        {
            return Crate.BRIQUE;
        }
        if (biome.is(Tags.Biomes.IS_FOREST) || biome.is(Tags.Biomes.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE))
        {
            return Crate.BOIS;
        }
        if (biome.is(Tags.Biomes.IS_PLAINS) || biome.is(Tags.Biomes.IS_SAVANNA))
        {
            return Crate.BLE;
        }
        return null;
    }

    /**
     * Advance every placed Comptoir by the game time elapsed since it last ran, so an unloaded one catches up
     * (within the configured limit) when its chunk loads again.
     */
    private static void runComptoirs(final MinecraftServer server)
    {
        final RegionData data = RegionData.get(server);
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            final RegionData.Comptoir comptoir = data.existingComptoir(colony);
            final Crate crate = data.regionOf(colony);
            if (comptoir == null || comptoir.pos == null || crate == null || !(colony.getWorld() instanceof ServerLevel level) || !level.isLoaded(comptoir.pos))
            {
                continue;
            }
            if (!(level.getBlockState(comptoir.pos).getBlock() instanceof ComptoirBlock))
            {
                comptoir.pos = null;
                data.touch();
                continue;
            }
            produce(level, colony, comptoir, crate);
            data.touch();
        }
    }

    private static void produce(final ServerLevel level, final IColony colony, final RegionData.Comptoir comptoir, final Crate crate)
    {
        final long now = level.getGameTime();
        final long maxElapsed = Math.max(PRODUCTION_INTERVAL * 2L, Config.COMPTOIR_CATCH_UP_MINUTES.get() * 1200L);
        final long elapsed = comptoir.lastTime < 0 ? 0 : Math.clamp(now - comptoir.lastTime, 0, maxElapsed);
        comptoir.lastTime = now;

        // Crates kept inside move to the warehouse as soon as there is one with room
        if (comptoir.stock > 0)
        {
            comptoir.stock -= CrateStores.storeInWarehouse(colony, crate, comptoir.stock);
        }

        final int interval = ticksPerCrate(comptoir.level);
        comptoir.progress += elapsed;
        final int made = (int) Math.min(Integer.MAX_VALUE, comptoir.progress / interval);
        if (made == 0)
        {
            return;
        }
        comptoir.progress -= (long) made * interval;

        final int stored = CrateStores.storeInWarehouse(colony, crate, made);
        final int room = Math.max(0, Config.COMPTOIR_STORAGE.get() - comptoir.stock);
        final int kept = Math.min(room, made - stored);
        comptoir.stock += kept;
        if (stored + kept < made)
        {
            // Full everywhere: production waits instead of piling up
            comptoir.progress = Math.min(comptoir.progress, interval);
        }
        if (stored + kept > 0)
        {
            final BlockPos pos = comptoir.pos;
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.02);
            level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.6F, 0.9F);
        }
    }

    private static int[] parseInts(final String text, final int[] fallback)
    {
        try
        {
            final String[] parts = text.split(",");
            final int[] values = new int[parts.length];
            for (int i = 0; i < parts.length; i++)
            {
                values[i] = Integer.parseInt(parts[i].trim());
            }
            return values.length == 0 ? fallback : values;
        }
        catch (final NumberFormatException e)
        {
            return fallback;
        }
    }
}
