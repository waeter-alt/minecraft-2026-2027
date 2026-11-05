package fr.kodistan.interregne.colony;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.mojang.math.Transformation;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.walls.WallBlocks;
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
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A colony wall rising along its trace, like the other sites: the blocks are set in order over a fixed time, a share
 * of them flying in, with dust, sounds and a boss bar.
 */
public final class WallSite implements Site
{
    private static final int FLIGHT_TICKS = 8;
    private static final int FLY_EVERY = 6;
    private static final int MAX_FLYING = 40;
    private static final int MAX_PER_TICK = 4096;

    private record Flight(BlockPos pos, BlockState state, Display.BlockDisplay display, int launchedAt)
    {
    }

    private final ServerLevel level;
    private final List<WallBlocks.Step> steps;
    private final int durationTicks;
    private final int wallLevel;
    private final Component title;
    private final ServerBossEvent bossBar;
    private final AABB area;
    private final Runnable onDone;
    /**
     * The colony's banner, copied onto every banner block of the wall.
     */
    @Nullable
    private final ItemStack banner;
    private final RandomSource random;
    private final List<Flight> flights = new ArrayList<>();

    private int next;
    private int age;
    private int solid;
    @Nullable
    private WallBlocks.Step last;

    /**
     * Where the colony lies: a villager caught in the wall is set down on this side of it.
     */
    private final BlockPos inside;
    /**
     * Who ordered the site: they see its bar wherever they watch from (the aerial view is far from their body).
     */
    @Nullable
    private final ServerPlayer watcher;
    /**
     * Every column the wall stands on.
     */
    private final java.util.Set<Long> columns = new java.util.HashSet<>();

    /**
     * Tearing the wall down: every step is a block broken, with its own particles and sound.
     */
    private final boolean demolition;

    private WallSite(final ServerLevel level, final List<WallBlocks.Step> steps, final int seconds, final int wallLevel, @Nullable final ItemStack banner,
      final BlockPos inside, @Nullable final ServerPlayer watcher, final Runnable onDone, final boolean demolition)
    {
        this.demolition = demolition;
        this.watcher = watcher;
        this.level = level;
        this.inside = inside;
        for (final WallBlocks.Step step : steps)
        {
            if (!step.state().isAir())
            {
                columns.add(fr.kodistan.interregne.walls.WallRegistry.cell(step.pos().getX(), step.pos().getZ()));
            }
        }
        this.banner = banner;
        this.steps = steps;
        this.durationTicks = Math.max(20, seconds * 20);
        this.wallLevel = wallLevel;
        this.onDone = onDone;
        this.random = level.getRandom();
        this.title = Component.literal(demolition ? "⚒ Démolition de la muraille" : "⚒ Muraille · niveau " + wallLevel);
        this.bossBar = new ServerBossEvent(title, BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
        this.bossBar.setProgress(0);
        AABB box = null;
        for (final WallBlocks.Step step : steps)
        {
            final AABB one = new AABB(step.pos());
            box = box == null ? one : box.minmax(one);
        }
        this.area = box == null ? new AABB(BlockPos.ZERO) : box;
    }

    /**
     * Starts the site; onDone runs once every block is in place.
     */
    public static void launch(final ServerLevel level, final List<WallBlocks.Step> steps, final int seconds, final int wallLevel,
      @Nullable final ItemStack banner, final BlockPos inside, @Nullable final ServerPlayer watcher, final Runnable onDone)
    {
        ConstructionManager.start(new WallSite(level, steps, seconds, wallLevel, banner, inside, watcher, onDone, false));
    }

    /**
     * Tears these blocks down in a few seconds, each broken with its particles and sound; onDone runs once all are gone.
     */
    public static void demolish(final ServerLevel level, final List<WallBlocks.Step> steps, final int seconds, @Nullable final ServerPlayer watcher,
      final Runnable onDone)
    {
        ConstructionManager.start(new WallSite(level, steps, seconds, 1, null, BlockPos.ZERO, watcher, onDone, true));
    }

    @Override
    public void start()
    {
        final BlockPos center = BlockPos.containing(area.getCenter());
        level.playSound(null, center, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.7F, 0.6F);
        level.playSound(null, center, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 0.8F);
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
        tickFlights(false);
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
        tickFlights(true);
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
        return wallLevel;
    }

    private void place(final WallBlocks.Step step, final boolean animate)
    {
        final BlockPos pos = step.pos();
        if (step.state().isAir())
        {
            if (demolition && !level.getBlockState(pos).isAir())
            {
                // Broken, not just removed: the crumbling is the show
                level.destroyBlock(pos, false);
                return;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        final BlockState state = Block.updateFromNeighbourShapes(step.state(), level, pos);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (banner != null && level.getBlockEntity(pos) instanceof BannerBlockEntity entity)
        {
            entity.applyComponentsFromItemStack(banner);
            entity.setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        }
        last = step;
        solid++;
        liftPlayersAt(pos);
        moveCreaturesInside(pos);
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
            launch(pos, state);
        }
    }

    /**
     * A villager (or any creature) standing where a block just went is set down just inside the wall, on the colony's
     * side, never pushed out of it.
     */
    private void moveCreaturesInside(final BlockPos pos)
    {
        for (final net.minecraft.world.entity.LivingEntity entity : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new AABB(pos),
          entity -> !(entity instanceof ServerPlayer)))
        {
            double dx = inside.getX() + 0.5 - entity.getX();
            double dz = inside.getZ() + 0.5 - entity.getZ();
            final double length = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
            dx /= length;
            dz /= length;
            for (int step = 1; step <= 24; step++)
            {
                final int x = (int) Math.floor(entity.getX() + dx * step);
                final int z = (int) Math.floor(entity.getZ() + dz * step);
                if (!columns.contains(fr.kodistan.interregne.walls.WallRegistry.cell(x, z)))
                {
                    final int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    entity.teleportTo(x + 0.5, y, z + 0.5);
                    if (entity instanceof net.minecraft.world.entity.Mob mob)
                    {
                        mob.getNavigation().stop();
                    }
                    break;
                }
            }
        }
    }

    /**
     * A player standing where a block just went is lifted onto the top of the column.
     */
    private void liftPlayersAt(final BlockPos pos)
    {
        final AABB block = new AABB(pos);
        for (final ServerPlayer player : level.players())
        {
            if (player.getBoundingBox().intersects(block))
            {
                final int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) + 2;
                player.teleportTo(player.getX(), Math.max(top, pos.getY() + 2), player.getZ());
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

    private void launch(final BlockPos pos, final BlockState state)
    {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        final Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        display.setBlockState(state);
        display.moveTo(pos.getX(), pos.getY(), pos.getZ());
        final float scale = 0.35F;
        final float offset = (1 - scale) / 2;
        display.setTransformation(new Transformation(
          new Vector3f(offset + (random.nextFloat() - 0.5F) * 3, 6 + random.nextFloat() * 2, offset + (random.nextFloat() - 0.5F) * 3),
          new Quaternionf().rotationXYZ(random.nextFloat() * 3.14F, random.nextFloat() * 3.14F, 0),
          new Vector3f(scale, scale, scale),
          new Quaternionf()));
        display.addTag(ConstructionSite.FLYING_TAG);
        level.addFreshEntity(display);
        flights.add(new Flight(pos, state, display, age));
    }

    private void tickFlights(final boolean landAll)
    {
        flights.removeIf(flight -> {
            final int flown = age - flight.launchedAt();
            if (!landAll && flown == 1)
            {
                flight.display().setTransformationInterpolationDelay(0);
                flight.display().setTransformationInterpolationDuration(FLIGHT_TICKS);
                flight.display().setTransformation(Transformation.identity());
            }
            if (landAll || flown > FLIGHT_TICKS + 1)
            {
                final BlockPos pos = flight.pos();
                final BlockState current = level.getBlockState(pos);
                if (current.isAir() || !current.getFluidState().isEmpty())
                {
                    level.setBlock(pos, Block.updateFromNeighbourShapes(flight.state(), level, pos), Block.UPDATE_ALL);
                    if (!landAll)
                    {
                        level.sendParticles(ParticleTypes.ENCHANTED_HIT, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.4, 0.4, 0.4, 0.1);
                    }
                }
                flight.display().discard();
                return true;
            }
            return false;
        });
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
            // Nearby players, and always the one who ordered it
            final net.minecraft.world.phys.Vec3 center = area.getCenter();
            final double reach = Math.max(area.getXsize(), area.getZsize()) / 2 + 96;
            for (final ServerPlayer player : level.players())
            {
                if (player == watcher || player.distanceToSqr(center.x, player.getY(), center.z) <= reach * reach)
                {
                    bossBar.addPlayer(player);
                }
                else
                {
                    bossBar.removePlayer(player);
                }
            }
            if (watcher != null && watcher.level() != level)
            {
                bossBar.removePlayer(watcher);
            }
        }
    }

    private void finish()
    {
        bossBar.setProgress(1);
        bossBar.removeAllPlayers();
        InterregneMod.LOGGER.info("Wall done: {} steps, {} ticks for {} planned", steps.size(), age, durationTicks);
        if (last != null)
        {
            final BlockPos pos = last.pos();
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 60, 1.5, 1.0, 1.5, 0.4);
            level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        onDone.run();
    }
}
