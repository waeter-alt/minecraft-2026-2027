// [claude-build] Regles du combat du Scaphandrier rouille, sans Minecraft (testees dans ScaphandrierLogiqueTest) :
// il chasse et frappe a la cle ; pret et a portee, il vise (on l'entend armer), puis tire son harpon a chaine en ligne droite.
// Le harpon qui touche le joueur le tire vers lui ; celui qui frappe un bloc le laisse coince (degats doubles) le temps de l'arracher.
package fr.atelier.grappin;

public final class ScaphandrierLogique {
    public enum Etat { CHASSE, VISEE, TIR, TRACTION, COINCE }

    /** Ou en est le harpon : pas lance (ou disparu), en vol, plante dans le joueur, plante dans un bloc. */
    public enum Harpon { AUCUN, EN_VOL, JOUEUR, BLOC }

    /** Durees en ticks. */
    public record Parametres(int delaiHarpon, int visee, int traction, int coince, int volMax) {
    }

    private ScaphandrierLogique() {
    }

    static Etat suivant(Etat etat, int ticksDansEtat, boolean cibleValide, boolean aPortee, int ticksDepuisHarpon, Harpon harpon,
            boolean joueurArrive, Parametres p) {
        return switch (etat) {
            case CHASSE -> cibleValide && aPortee && ticksDepuisHarpon >= p.delaiHarpon() ? Etat.VISEE : Etat.CHASSE;
            case VISEE -> !cibleValide ? Etat.CHASSE : ticksDansEtat >= p.visee() ? Etat.TIR : Etat.VISEE;
            case TIR -> switch (harpon) {
                case JOUEUR -> Etat.TRACTION;
                case BLOC -> Etat.COINCE;
                case AUCUN -> Etat.CHASSE;
                case EN_VOL -> ticksDansEtat >= p.volMax() ? Etat.CHASSE : Etat.TIR;
            };
            case TRACTION -> harpon != Harpon.JOUEUR || joueurArrive || ticksDansEtat >= p.traction() ? Etat.CHASSE : Etat.TRACTION;
            case COINCE -> ticksDansEtat >= p.coince() ? Etat.CHASSE : Etat.COINCE;
        };
    }

    static float degats(Etat etat, float montant, double facteurCoince) {
        return etat == Etat.COINCE ? (float) (montant * facteurCoince) : montant;
    }

    /** Etat du hublot de face selon la part de vie restante : 0 intact, 1 et 2 fissures, 3 eclate. Effet visuel seulement. */
    static int hublot(float partDeVie) {
        return partDeVie <= 0.12F ? 3 : partDeVie <= 0.33F ? 2 : partDeVie <= 0.66F ? 1 : 0;
    }
}
