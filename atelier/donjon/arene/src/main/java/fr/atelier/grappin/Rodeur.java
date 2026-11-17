// [claude-build] Le Rodeur des Racines, mini-boss du sous-sol 1 du Donjon Suspendu : bete de racines a quatre pattes (modele et animations :
// atelier/modeles/rodeur/rodeur.bbmodel). Ici, l'entite ; le combat (charge, sonne) s'ajoute avec RodeurLogique.
package fr.atelier.grappin;

import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

public class Rodeur extends Monster {
    // Animations, cote client (jouees par RodeurModele)
    final AnimationState repos = new AnimationState();
    final AnimationState morsure = new AnimationState();
    final AnimationState grattage = new AnimationState();
    final AnimationState charge = new AnimationState();
    final AnimationState sonne = new AnimationState();
    final AnimationState reveil = new AnimationState();
    final AnimationState mort = new AnimationState();

    public Rodeur(EntityType<? extends Rodeur> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder attributs() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 150.0)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            if (this.tickCount < 5 && !reveil.isStarted()) {
                reveil.start(this.tickCount);                   // il sort de la mousse quand il apparait
            }
            repos.animateWhen(this.deathTime == 0, this.tickCount);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 3) {
            mort.start(this.tickCount);                         // mort (envoye par LivingEntity.die)
        }
        super.handleEntityEvent(id);
    }
}
