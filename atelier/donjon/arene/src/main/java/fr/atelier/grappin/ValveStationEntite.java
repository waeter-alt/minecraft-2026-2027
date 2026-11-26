// [claude-build] Donnees d'affichage de la valve de la piscine, cote client : l'animation « tourner » joue quand la valve passe a tournee. Rien n'est sauvegarde :
// l'etat est celui du bloc (tournee) ; au chargement une valve deja tournee est dessinee au repos, sans rejouer les trois tours.
package fr.atelier.grappin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ValveStationEntite extends BlockEntity {
    final AnimationState tourner = new AnimationState();
    private Boolean dernier;                   // dernier etat vu (null : jamais dessinee)

    public ValveStationEntite(BlockPos pos, BlockState etat) {
        super(StationBlocs.VALVE_ENTITE.get(), pos, etat);
    }

    /** Appele a chaque image : lance l'animation quand la valve vient d'etre tournee (deja finie si on la voit pour la premiere fois). */
    void suivre(boolean tournee, int tick) {
        if (dernier != null && dernier == tournee) {
            return;
        }
        if (tournee) {
            tourner.start(DonjonLogique.depart(dernier == null, tick));
        } else {
            tourner.stop();                    // remise a zero : le volant revient au repos d'un coup
        }
        dernier = tournee;
    }
}
