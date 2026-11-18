// [claude-build] Donnees d'affichage du levier de racine, cote client : quelle animation joue (tirer ou relacher) et depuis quand.
// Rien n'est sauvegarde : l'etat du levier est celui du bloc (powered) ; au chargement il est dessine directement dans sa position.
package fr.atelier.grappin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class LevierEntite extends BlockEntity {
    final AnimationState tirer = new AnimationState();
    final AnimationState relacher = new AnimationState();
    private Boolean dernier;                   // dernier etat vu (null : jamais dessine)

    public LevierEntite(BlockPos pos, BlockState etat) {
        super(DonjonBlocs.LEVIER_ENTITE.get(), pos, etat);
    }

    /** Appele a chaque image : lance l'animation quand le levier change d'etat (ou la pose finie au premier affichage). */
    void suivre(boolean tire, int tick) {
        if (dernier != null && dernier == tire) {
            return;
        }
        int depart = DonjonLogique.depart(dernier == null, tick);
        (tire ? relacher : tirer).stop();
        (tire ? tirer : relacher).start(depart);
        dernier = tire;
    }
}
