// [claude-build] Le cristal des mers : le cristal de l'End dont on a juste change la couleur (bleu-vert), pour les combats du Donjon de la Station (le Leviathan et le Scaphandrier)
// comme celui contre l'Ender Dragon. Tant qu'il tient, il soigne le boss le plus proche (CRISTAL_SOIN points de vie par seconde, un rayon le relie au boss) ; un coup le brise,
// sans explosion. Le datapack du donjon les invoque autour de l'arene (summon donjon:cristal_marin) et sait quand il n'en reste plus.
package fr.atelier.grappin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.Level;

public class CristalMarin extends EndCrystal {
    private static final int PERIODE = 10;                                      // soigne tous les 10 ticks

    public CristalMarin(EntityType<? extends CristalMarin> type, Level niveau) {
        super(type, niveau);
    }

    @Override
    public boolean hurt(DamageSource source, float montant) {
        if (isInvulnerableTo(source) || source.getEntity() instanceof Leviathan) {
            return false;
        }
        if (!this.level().isClientSide && !isRemoved()) {
            if (this.level() instanceof ServerLevel niveau) {
                niveau.sendParticles(ParticleTypes.GLOW, getX(), getY() + 1.0, getZ(), 40, 0.5, 0.6, 0.5, 0.2);
                niveau.sendParticles(ParticleTypes.BUBBLE_POP, getX(), getY() + 1.0, getZ(), 30, 0.6, 0.6, 0.6, 0.1);
            }
            this.level().playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 3.0F, 0.7F);
            this.level().playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 3.0F, 0.6F);
            discard();                                                          // pas d'explosion, contrairement au cristal de l'End
        }
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel niveau && this.tickCount % 2 == 0) {
            LivingEntity boss = boss(niveau);
            // le rayon, comme celui de l'Ender Dragon : le client le dessine jusqu'a cette case (CristalMarinRendu)
            setBeamTarget(boss == null ? null : BlockPos.containing(boss.getX(), boss.getY() + boss.getBbHeight() / 2, boss.getZ()));
            if (boss != null && this.tickCount % PERIODE == 0) {
                soigner(niveau, boss);
            }
        }
    }

    /** Le boss le plus proche que ce cristal soigne : le Leviathan ou le Scaphandrier. */
    private LivingEntity boss(ServerLevel niveau) {
        LivingEntity proche = null;
        for (LivingEntity l : niveau.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(Reglages.CRISTAL_PORTEE.get()),
                e -> e.isAlive() && (e instanceof Leviathan || e instanceof Scaphandrier))) {
            if (proche == null || l.distanceToSqr(this) < proche.distanceToSqr(this)) {
                proche = l;
            }
        }
        return proche;
    }

    private void soigner(ServerLevel niveau, LivingEntity boss) {
        if (boss.getHealth() < boss.getMaxHealth()) {
            boss.heal((float) (Reglages.CRISTAL_SOIN.get() * PERIODE / 20.0));
        }
        niveau.sendParticles(ParticleTypes.HEART, boss.getX(), boss.getY() + boss.getBbHeight(), boss.getZ(), 1, 0.8, 0.3, 0.8, 0.0);
    }
}
