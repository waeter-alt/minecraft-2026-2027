// [claude-build] Regles du combat du Rodeur des Racines, sans Minecraft (testees dans RodeurLogiqueTest) :
// il chasse et mord ; quand il est pret et a portee, il gratte le sol (on le voit venir), puis charge en ligne droite ;
// une charge arretee par un mur ou un pilier le laisse sonne (degats doubles), une charge qui touche un joueur ou qui va
// au bout de sa portee le remet en chasse.
package fr.atelier.grappin;

public final class RodeurLogique {
    public enum Etat { CHASSE, GRATTE, CHARGE, SONNE }

    /** Durees en ticks, portee de la charge en blocs. */
    public record Parametres(int grattage, int sonne, int delaiCharge, double portee) {
    }

    private RodeurLogique() {
    }

    static Etat suivant(Etat etat, int ticksDansEtat, boolean cibleValide, boolean aPortee, boolean bloque, boolean toucheJoueur,
            double distanceParcourue, int ticksDepuisCharge, Parametres p) {
        return switch (etat) {
            case CHASSE -> cibleValide && aPortee && ticksDepuisCharge >= p.delaiCharge() ? Etat.GRATTE : Etat.CHASSE;
            case GRATTE -> !cibleValide ? Etat.CHASSE : ticksDansEtat >= p.grattage() ? Etat.CHARGE : Etat.GRATTE;
            case CHARGE -> toucheJoueur || distanceParcourue >= p.portee() ? Etat.CHASSE : bloque ? Etat.SONNE : Etat.CHARGE;
            case SONNE -> ticksDansEtat >= p.sonne() ? Etat.CHASSE : Etat.SONNE;
        };
    }

    static float degats(Etat etat, float montant, double facteurSonne) {
        return etat == Etat.SONNE ? (float) (montant * facteurSonne) : montant;
    }
}
