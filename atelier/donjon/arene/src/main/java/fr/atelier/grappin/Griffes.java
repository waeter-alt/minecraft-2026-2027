// [claude-build] Ouverture des 4 griffes du crochet lance (0 = repliees le long de la tige, 1 = grandes ouvertes), sans Minecraft
// (testee dans GriffesTest) : elles s'ouvrent en 4 ticks apres le tir, puis se referment a moitie en 3 ticks sur l'ancrage.
package fr.atelier.grappin;

public final class Griffes {
    static final float TICKS_OUVERTURE = 4;
    static final float TICKS_SERRAGE = 3;
    static final float SERREES = 0.5F;
    static final float ANGLE_REPLIE = 80;           // degres entre une griffe ouverte et une griffe repliee

    private Griffes() {
    }

    static float ouverture(float ticksDeVol, boolean accroche, float ticksDepuisAccroche) {
        if (!accroche) {
            return Math.min(1, ticksDeVol / TICKS_OUVERTURE);
        }
        return 1 - (1 - SERREES) * Math.min(1, ticksDepuisAccroche / TICKS_SERRAGE);
    }
}
