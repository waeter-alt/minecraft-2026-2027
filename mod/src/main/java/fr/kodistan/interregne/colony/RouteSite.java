package fr.kodistan.interregne.colony;

import com.minecolonies.api.colony.buildings.IBuilding;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.routes.RoutePath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A trade route rising along its traced path, like the other sites: same show as {@link WallSite} (blocks flying in,
 * dust, sounds, a boss bar), reusing {@link FlyingBlocks} and {@link SiteEffects} rather than a third copy of them.
 */
public final class RouteSite implements Site
{
    private static final int FLIGHT_TICKS = 8;
    private static final int FLY_EVERY = 5;
    private static final int MAX_FLYING = 40;
    private static final int MAX_PER_TICK = 4096;

    private final ServerLevel level;
    private final List<RoutePath.Step> steps;
    private final int durationTicks;
    private final Component title;
    private final ServerBossEvent bossBar;
    private final AABB area;
    private final Runnable onDone;
    private final RandomSource random;
    private final List<FlyingBlocks.Flight> flights = new ArrayList<>();

    private int next;
    private int age;
    private int solid;
    @Nullable
    private RoutePath.Step last;

    private RouteSite(final ServerLevel level, final List<RoutePath.Step> steps, final int seconds, final Component title, final Runnable onDone)
    {
        this.level = level;
        this.steps = steps;
        this.durationTicks = Math.max(20, seconds * 20);
        this.title = title;
        this.onDone = onDone;
        this.random = level.getRandom();
        this.bossBar = new ServerBossEvent(title, BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
        this.bossBar.setProgress(0);
        AABB box = null;
        for (final RoutePath.Step step : steps)
        {
            final AABB one = new AABB(step.pos());
            box = box == null ? one : box.minmax(one);
        }
        this.area = box == null ? new AABB(BlockPos.ZERO) : box;
    }

    /**
     * Starts the site; onDone runs once every block of the route is in place.
     */
    public static void launch(final ServerLevel level, final List<RoutePath.Step> steps, final int seconds, final Component title, final Runnable onDone)
    {
        ConstructionManager.start(new RouteSite(level, steps, seconds, title, onDone));
    }

    @Override
    public void start()
    {
        final BlockPos center = BlockPos.containing(area.getCenter());
        level.playSound(null, center, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.7F, 0.9F);
        level.playSound(null, center, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 1.1F);
        updateBossBar(true);
    }

    @Override
    public boolean tick()
    {
        age++;
        final int target = age >= durationTicks ? steps.size() : (int) Math.ceil((double) steps.size() * age / durationTicks);
        int placedNow = 0;
        while (next < Math.min(target, steps.size()) && placedNow++ < MAX_PER_TICK)
        {
            place(steps.get(next++), true);
        }
        effects();
        if (age % 5 == 0)
        {
            updateBossBar(age % 20 == 0);
        }
        FlyingBlocks.tickFlights(level, flights, age, false, FLIGHT_TICKS);
        if (next >= steps.size() && flights.isEmpty())
        {
            finish();
            return true;
        }
        return false;
    }

    @Override
    public void finishNow()
    {
        while (next < steps.size())
        {
            place(steps.get(next++), false);
        }
        FlyingBlocks.tickFlights(level, flights, age, true, FLIGHT_TICKS);
        finish();
    }

    @Nullable
    @Override
    public IBuilding building()
    {
        return null;
    }

    @Override
    public int targetLevel()
    {
        return 1;
    }

    private void place(final RoutePath.Step step, final boolean animate)
    {
        final BlockPos pos = step.pos();
        if (step.state().isAir())
        {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        BlockState state = Block.updateFromNeighbourShapes(step.state(), level, pos);
        if (state.isAir() && !step.state().isAir())
        {
            state = step.state();
        }
        level.setBlock(pos, state, Block.UPDATE_ALL);
        last = step;
        solid++;
        liftPlayersAt(pos);
        if (!animate)
        {
            return;
        }
        if (solid % 3 == 0)
        {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0);
        }
        if (solid % FLY_EVERY == 0 && flights.size() < MAX_FLYING && state.isCollisionShapeFullBlock(level, pos) && !(state.getBlock() instanceof FallingBlock))
        {
            flights.add(FlyingBlocks.launch(level, pos, state, age, 6, random));
        }
    }

    /**
     * A player standing where a route block just went (a tunnel ceiling closing in, a bridge deck rising under them)
     * is lifted onto it rather than buried or dunked.
     */
    private void liftPlayersAt(final BlockPos pos)
    {
        final AABB block = new AABB(pos);
        for (final ServerPlayer player : level.players())
        {
            if (player.getBoundingBox().intersects(block))
            {
                player.teleportTo(player.getX(), pos.getY() + 1, player.getZ());
            }
        }
    }

    private void effects()
    {
        if (last == null)
        {
            return;
        }
        final BlockPos pos = last.pos();
        if (age % 3 == 0)
        {
            level.playSound(null, pos, last.state().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.7F, 0.8F + random.nextFloat() * 0.4F);
        }
        if (age % 4 == 0)
        {
            level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 3, 0.4, 0.2, 0.4, 0.02);
        }
        if (age % 10 == 0)
        {
            level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 12, 0.6, 0.6, 0.6, 0.4);
        }
    }

    private void updateBossBar(final boolean refreshViewers)
    {
        bossBar.setProgress(Math.min(1.0F, (float) age / durationTicks));
        if (age % 20 == 0 || age <= 1)
        {
            bossBar.setName(SiteEffects.withCountdown(title, durationTicks - age));
        }
        if (refreshViewers)
        {
            SiteEffects.showToNearby(bossBar, level, area);
        }
    }

    private void finish()
    {
        bossBar.setProgress(1);
        bossBar.removeAllPlayers();
        InterregneMod.LOGGER.info("Route done: {} steps, {} ticks for {} planned", steps.size(), age, durationTicks);
        if (last != null)
        {
            final BlockPos pos = last.pos();
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 60, 1.5, 1.0, 1.5, 0.4);
            level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        onDone.run();
    }
}
