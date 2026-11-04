package fr.kodistan.interregne.colony;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.workorders.IServerWorkOrder;
import com.minecolonies.api.entity.ai.workers.util.IBuilderUndestroyable;
import com.minecolonies.core.colony.workorders.AbstractWorkOrder;
import com.mojang.math.Transformation;
import fr.kodistan.interregne.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * One demolition in progress: takes the building apart from the roof down over a set duration, the reverse show
 * of a construction (blocks crumbling, some flying off into the sky, dust, blasts, a red countdown bar).
 * Like MineColonies' builders, it only removes the building's own blocks and leaves the hut block to pick up.
 */
public final class DemolitionSite implements Site
{
    private static final int   FLIGHT_TICKS          = 12;
    private static final int   MAX_BREAK_FX_PER_TICK = 4;

    /**
     * A block thrown off the building, shown flying away until it fades.
     */
    private record Flight(Display.BlockDisplay display, int launchedAt)
    {
    }

    private final ServerLevel      level;
    private final IColony          colony;
    private final IServerWorkOrder order;
    @Nullable
    private final IBuilding        building;
    private final ServerBossEvent  bossBar;
    private final Component        title;
    private final AABB             area;
    private final RandomSource     random;
    private final List<BlockPos>   toRemove;
    private final List<Flight>     flights = new ArrayList<>();
    private final int              durationTicks;

    private int age;
    private int removed;

    public DemolitionSite(final ServerLevel level, final IColony colony, final IServerWorkOrder order, @Nullable final IBuilding building, final Blueprint blueprint)
    {
        this.level = level;
        this.colony = colony;
        this.order = order;
        this.building = building;
        this.random = level.getRandom();
        this.toRemove = blocksToRemove(level, order.getLocation(), blueprint, random);
        this.durationTicks = Config.DEMOLITION_SECONDS.get() * 20;
        this.area = order instanceof AbstractWorkOrder abstractOrder && abstractOrder.getBoundingBox() != null
                      ? abstractOrder.getBoundingBox()
                      : new AABB(order.getLocation()).inflate(Math.max(blueprint.getSizeX(), blueprint.getSizeZ()) / 2.0);
        this.title = Component.literal("⛏ Démolition : ").append(DecoNames.of(order));
        this.bossBar = new ServerBossEvent(title, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    }

    /**
     * Seconds a demolition lasts, for messages.
     */
    public static int durationSeconds()
    {
        return Config.DEMOLITION_SECONDS.get();
    }

    @Override
    public void start()
    {
        final BlockPos center = BlockPos.containing(area.getCenter());
        level.playSound(null, center, SoundEvents.RAVAGER_ROAR, SoundSource.BLOCKS, 0.6F, 0.7F);
        level.playSound(null, center, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.7F, 0.5F);
        updateBossBar(true);
    }

    @Override
    public boolean tick()
    {
        age++;
        final int target = Math.min(toRemove.size(), (int) Math.ceil((double) toRemove.size() * age / durationTicks));
        int effects = 0;
        while (removed < target)
        {
            removeNext(effects++ < MAX_BREAK_FX_PER_TICK);
        }

        if (age % 6 == 0 && removed > 0)
        {
            final BlockPos last = toRemove.get(removed - 1);
            level.sendParticles(ParticleTypes.POOF, last.getX() + 0.5, last.getY() + 0.5, last.getZ() + 0.5, 4, 0.6, 0.4, 0.6, 0.02);
        }
        if (age % 20 == 0 && removed > 0)
        {
            SiteEffects.blastPuff(level, area, toRemove.get(removed - 1).getY() + 0.5, random);
            SiteEffects.chimneySmoke(level, area);
        }
        if (age % 5 == 0)
        {
            updateBossBar(age % 20 == 0);
        }

        tickFlights(false);

        if (removed >= toRemove.size() && flights.isEmpty())
        {
            finish();
            return true;
        }
        return false;
    }

    @Override
    public void finishNow()
    {
        while (removed < toRemove.size())
        {
            removeNext(false);
        }
        tickFlights(true);
        finish();
    }

    private void removeNext(final boolean withEffects)
    {
        final BlockPos pos = toRemove.get(removed++);
        final BlockState state = level.getBlockState(pos);
        if (state.isAir())
        {
            return;
        }
        if (withEffects)
        {
            // Break particles and sound, without drops
            level.destroyBlock(pos, false);
        }
        else
        {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }

        final int share = Config.FLYING_SHARE.get();
        if (share > 0 && removed % share == 0 && flights.size() < Config.MAX_FLYING.get() && !state.hasBlockEntity())
        {
            throwOff(pos, state);
        }
    }

    /**
     * The removed block shows as a display that spins up and away while shrinking.
     */
    private void throwOff(final BlockPos pos, final BlockState state)
    {
        final Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        display.setBlockState(state);
        display.moveTo(pos.getX(), pos.getY(), pos.getZ());
        display.setTransformation(Transformation.identity());
        display.addTag(ConstructionSite.FLYING_TAG);
        level.addFreshEntity(display);
        flights.add(new Flight(display, age));
    }

    private void tickFlights(final boolean dropAll)
    {
        flights.removeIf(flight -> {
            final int flown = age - flight.launchedAt();
            if (!dropAll && flown == 1)
            {
                final float scale = 0.15F;
                final float offset = (1 - scale) / 2;
                flight.display().setTransformationInterpolationDelay(0);
                flight.display().setTransformationInterpolationDuration(FLIGHT_TICKS);
                flight.display().setTransformation(new Transformation(
                  new Vector3f(offset + (random.nextFloat() - 0.5F) * 4, 7.0F + random.nextFloat() * 3, offset + (random.nextFloat() - 0.5F) * 4),
                  new Quaternionf().rotationXYZ(random.nextFloat() * 6.28F, random.nextFloat() * 6.28F, 0),
                  new Vector3f(scale, scale, scale),
                  new Quaternionf()));
            }
            if (dropAll || flown > FLIGHT_TICKS + 1)
            {
                final Vec3 pos = flight.display().position();
                if (!dropAll)
                {
                    level.sendParticles(ParticleTypes.POOF, pos.x + 0.5, pos.y + 8, pos.z + 0.5, 2, 0.2, 0.2, 0.2, 0.01);
                }
                flight.display().discard();
                return true;
            }
            return false;
        });
    }

    private void finish()
    {
        bossBar.setProgress(1);
        bossBar.removeAllPlayers();
        final Vec3 center = area.getCenter();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, area.minY + 1, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.POOF, center.x, area.minY + 1, center.z, 60, area.getXsize() / 3, 1.0, area.getZsize() / 3, 0.05);
        level.playSound(null, center.x, area.minY, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.6F, 0.8F);
        InstantBuilder.completeDemolition(colony, order, building);
        colony.getMessagePlayerEntities().forEach(p -> p.sendSystemMessage(
          Component.literal("Démolition terminée : ").append(DecoNames.of(order)).withStyle(ChatFormatting.GRAY)));
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

    /**
     * The building's own blocks still standing, roof first, shuffled within each layer so it crumbles unevenly.
     * Same rules as MineColonies' builders: skip placeholders, huts and other undestroyable blocks, and water.
     */
    private static List<BlockPos> blocksToRemove(final ServerLevel level, final BlockPos anchor, final Blueprint blueprint, final RandomSource random)
    {
        final BlockState[] palette = blueprint.getPalette();
        final short[][][] structure = blueprint.getStructure();
        final BlockPos origin = anchor.subtract(blueprint.getPrimaryBlockOffset());
        final List<BlockPos> positions = new ArrayList<>();
        for (int y = 0; y < structure.length; y++)
        {
            for (int z = 0; z < structure[y].length; z++)
            {
                for (int x = 0; x < structure[y][z].length; x++)
                {
                    final BlockState planned = palette[structure[y][z][x]];
                    if (planned.isAir() || BuiltInRegistries.BLOCK.getKey(planned.getBlock()).getNamespace().equals("structurize"))
                    {
                        continue;
                    }
                    final BlockPos pos = origin.offset(x, y, z);
                    final BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && !(state.getBlock() instanceof IBuilderUndestroyable) && state.getBlock() != Blocks.WATER)
                    {
                        positions.add(pos);
                    }
                }
            }
        }
        Collections.shuffle(positions, new java.util.Random(random.nextLong()));
        positions.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed());
        return positions;
    }
}
