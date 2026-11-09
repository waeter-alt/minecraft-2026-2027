// [claude-build] Le crochet (grappin 2.0) : vole tout droit (sans gravite) jusqu'a la portee, et ne s'accroche qu'a un point d'ancrage
// (tag grappin:ancrages) ; sur tout autre bloc il rebondit et disparait. Accroche, il ne tire plus le joueur : le balancier est
// calcule chez le joueur (GrappinClient), comme tout mouvement de joueur dans Minecraft. Ici, cote serveur : decrocher quand le
// joueur s'accroupit ou s'eloigne, et pas de degats de chute tant qu'il est tenu par la corde (ils reviennent apres le lacher).
package fr.atelier.grappin;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class Crochet extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Boolean> ACCROCHE = SynchedEntityData.defineId(Crochet.class, EntityDataSerializers.BOOLEAN);

    private int tempsAccroche = 0;

    public Crochet(EntityType<? extends Crochet> type, Level level) {
        super(type, level);
    }

    public Crochet(Level level, LivingEntity lanceur) {
        super(Grappin.CROCHET.get(), lanceur, level);
    }

    /** Les crochets en jeu lances par ce joueur. */
    public static List<Crochet> crochetsDe(Player joueur) {
        double r = Reglages.PORTEE.get() + 20;
        return joueur.level().getEntitiesOfClass(Crochet.class, joueur.getBoundingBox().inflate(r), c -> c.getOwner() == joueur);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ACCROCHE, false);
    }

    public boolean estAccroche() {
        return this.entityData.get(ACCROCHE);
    }

    @Override
    protected Item getDefaultItem() {
        return Grappin.GRAPPIN.get();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;                                     // le crochet file tout droit
    }

    @Override
    public void tick() {
        if (!estAccroche()) {
            Vec3 avant = position();
            super.tick();
            if (isRemoved() || estAccroche()) {
                return;
            }
            Entity lanceur = getOwner();
            if (!level().isClientSide) {
                if (aimanter(avant, position())) {
                    return;                             // il a frole un point d'ancrage : accroche
                }
                if (lanceur == null || lanceur.distanceTo(this) > Reglages.PORTEE.get()) {
                    discard();                          // trop loin sans trouver d'ancrage : le crochet revient
                }
            }
            return;
        }
        this.setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide) {
            return;
        }
        Entity lanceur = getOwner();
        if (!(lanceur instanceof Player joueur) || !joueur.isAlive() || joueur.level() != level()
                || joueur.distanceTo(this) > Reglages.PORTEE.get() + 8) {
            discard();
            return;
        }
        tempsAccroche++;
        if (tempsAccroche > 4 && joueur.isShiftKeyDown()) {     // s'accroupir : lacher (la vitesse est gardee chez le joueur)
            rappeler();
            return;
        }
        joueur.fallDistance = 0;                        // tenu par la corde : la chute ne compte qu'a partir du lacher
    }

    /** Aide a la visee : si le trajet de ce tick passe pres d'un point d'ancrage visible, le crochet s'y accroche. */
    private boolean aimanter(Vec3 de, Vec3 a) {
        double r = Reglages.AIMANT.get();
        if (r <= 0) {
            return false;
        }
        BlockPos meilleur = null;
        double dmin = r;
        Vec3 dir = a.subtract(de);
        double lg = dir.length();
        int n = (int) Math.ceil(r);
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(Math.min(de.x, a.x) - n, Math.min(de.y, a.y) - n, Math.min(de.z, a.z) - n),
                BlockPos.containing(Math.max(de.x, a.x) + n, Math.max(de.y, a.y) + n, Math.max(de.z, a.z) + n))) {
            if (!level().getBlockState(p).is(Grappin.ANCRAGES)) {
                continue;
            }
            Vec3 c = Vec3.atCenterOf(p);
            double t = lg < 1.0E-6 ? 0 : Math.max(0, Math.min(1, c.subtract(de).dot(dir) / (lg * lg)));
            double d = c.distanceTo(de.add(dir.scale(t))) - 0.5;            // distance du trajet a la surface du bloc (a peu pres)
            if (d < dmin) {
                ClipContext vue = new ClipContext(de.add(dir.scale(t)), c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this);
                BlockHitResult h = level().clip(vue);
                if (h.getType() == HitResult.Type.MISS || h.getBlockPos().equals(p)) {     // pas de bloc entre les deux
                    dmin = d;
                    meilleur = p.immutable();
                }
            }
        }
        if (meilleur == null) {
            return false;
        }
        Vec3 c = Vec3.atCenterOf(meilleur);
        Vec3 depuis = position().subtract(c);
        Vec3 point = c.add(depuis.normalize().scale(0.55));                 // sur la face tournee vers le crochet
        setPos(point.x, point.y, point.z);
        setDeltaMovement(Vec3.ZERO);
        this.entityData.set(ACCROCHE, true);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
        return true;
    }

    @Override
    protected void onHitBlock(BlockHitResult touche) {
        super.onHitBlock(touche);
        if (level().isClientSide) {
            return;
        }
        if (!level().getBlockState(touche.getBlockPos()).is(Grappin.ANCRAGES)) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.8F, 1.6F);
            discard();                                  // pas un point d'ancrage : le crochet ne tient pas
            return;
        }
        Vec3 point = touche.getLocation();
        setPos(point.x, point.y, point.z);
        setDeltaMovement(Vec3.ZERO);
        this.entityData.set(ACCROCHE, true);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    @Override
    protected void onHitEntity(EntityHitResult touche) {
        if (!level().isClientSide) {
            discard();                                  // le crochet ne blesse personne
        }
    }

    /** Decroche et fait disparaitre le crochet. */
    public void rappeler() {
        if (!level().isClientSide && estAccroche()) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.6F, 1.2F);
        }
        discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;                                   // jamais enregistre avec le monde
    }
}
