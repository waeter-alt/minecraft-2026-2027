// [claude-build] Entite de la pompe de la station, cote client seulement : l'angle du volant, qui avance tant que la pompe est en marche
// et reste ou il est a l'arret (rien n'est sauvegarde).
package fr.atelier.grappin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PompeEntite extends BlockEntity {
    /** Degres par tick : un tour toutes les 3 secondes. */
    static final float VITESSE = 6.0F;
    float angle;
    private float dernierTemps = Float.NaN;

    public PompeEntite(BlockPos pos, BlockState etat) {
        super(StationBlocs.POMPE_ENTITE.get(), pos, etat);
    }

    /** Appele a chaque image par le rendu ; t en ticks (partiel compris). */
    void avancer(float t, boolean enMarche) {
        float dt = Float.isNaN(dernierTemps) ? 0 : Math.max(0, Math.min(5, t - dernierTemps));
        dernierTemps = t;
        if (enMarche) {
            angle = (angle + dt * VITESSE) % 360;
        }
    }
}
