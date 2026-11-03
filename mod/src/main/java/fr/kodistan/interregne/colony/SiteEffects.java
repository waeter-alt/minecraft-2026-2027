package fr.kodistan.interregne.colony;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Effects shared by construction and demolition sites. All vanilla particles and sounds, sent to nearby players only.
 */
final class SiteEffects
{
    private static final double VIEW_DISTANCE_SQ = 48 * 48;

    private SiteEffects()
    {
    }

    /**
     * Show the bar to players near the site, hide it from the others.
     */
    static void showToNearby(final ServerBossEvent bar, final ServerLevel level, final AABB area)
    {
        final Vec3 center = area.getCenter();
        for (final ServerPlayer player : level.players())
        {
            if (player.distanceToSqr(center) <= VIEW_DISTANCE_SQ)
            {
                bar.addPlayer(player);
            }
            else
            {
                bar.removePlayer(player);
            }
        }
    }

    /**
     * "title — 1:05"
     */
    static Component withCountdown(final Component title, final int remainingTicks)
    {
        final int seconds = Math.max(0, (remainingTicks + 19) / 20);
        return title.copy().append(Component.literal(" — " + seconds / 60 + ":" + String.format("%02d", seconds % 60)).withStyle(ChatFormatting.GRAY));
    }

    /**
     * Smoke rising from the four corners, like a worksite's chimneys.
     */
    static void chimneySmoke(final ServerLevel level, final AABB area)
    {
        final double[][] corners = {{area.minX, area.minZ}, {area.maxX, area.minZ}, {area.minX, area.maxZ}, {area.maxX, area.maxZ}};
        for (final double[] corner : corners)
        {
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, corner[0], area.minY + 0.5, corner[1], 1, 0.1, 0, 0.1, 0.01);
        }
    }

    /**
     * The TNT blast puff with a muffled bang, somewhere on the site at the given height: a crew at work.
     */
    static void blastPuff(final ServerLevel level, final AABB area, final double y, final RandomSource random)
    {
        final double x = Mth.lerp(random.nextDouble(), area.minX, area.maxX);
        final double z = Mth.lerp(random.nextDouble(), area.minZ, area.maxZ);
        level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0, 0, 0, 0);
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.25F, 1.4F + random.nextFloat() * 0.3F);
    }

    /**
     * Enchanting runes drifting along the footprint's outline.
     */
    static void runeOutline(final ServerLevel level, final AABB area, final int density)
    {
        final double y = area.minY + 0.2;
        final double perimeter = 2 * (area.getXsize() + area.getZsize());
        final double step = Math.max(1.0, perimeter / 160) / density;
        for (double d = 0; d < perimeter; d += step)
        {
            final double x;
            final double z;
            if (d < area.getXsize())
            {
                x = area.minX + d;
                z = area.minZ;
            }
            else if (d < area.getXsize() + area.getZsize())
            {
                x = area.maxX;
                z = area.minZ + d - area.getXsize();
            }
            else if (d < 2 * area.getXsize() + area.getZsize())
            {
                x = area.maxX - (d - area.getXsize() - area.getZsize());
                z = area.maxZ;
            }
            else
            {
                x = area.minX;
                z = area.maxZ - (d - 2 * area.getXsize() - area.getZsize());
            }
            level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 1, 0, 0.3, 0, 0.5);
        }
    }
}
