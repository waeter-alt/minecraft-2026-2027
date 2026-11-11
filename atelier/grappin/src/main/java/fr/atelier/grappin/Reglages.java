// [claude-build] Tous les reglages du grappin, au meme endroit : fichier config/grappin-common.toml (modifiable sans recompiler).
package fr.atelier.grappin;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Reglages {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue PORTEE = B.comment("Portee maximale du tir, en blocs.").defineInRange("portee", 30.0, 4.0, 64.0);
    public static final ModConfigSpec.DoubleValue VITESSE_CROCHET = B.comment("Vitesse du crochet en vol, en blocs par tick.").defineInRange("vitesseCrochet", 2.5, 0.5, 6.0);
    public static final ModConfigSpec.DoubleValue AIMANT = B.comment("Le crochet s'accroche a un point d'ancrage qu'il frole a moins de cette distance, en blocs (0 = il faut le toucher).").defineInRange("aimantAncrage", 1.5, 0.0, 4.0);
    public static final ModConfigSpec.IntValue RECHARGE = B.comment("Temps de recharge apres un tir, en ticks (20 = 1 s).").defineInRange("recharge", 6, 0, 200);
    public static final ModConfigSpec.DoubleValue GRAVITE = B.comment("Gravite pendant le balancier, en multiple de la gravite normale (1 = normale).").defineInRange("graviteBalancier", 1.25, 0.2, 4.0);
    public static final ModConfigSpec.DoubleValue INFLUENCE = B.comment("Poussee des touches de deplacement pendant le balancier, en blocs par tick (par tick).").defineInRange("influenceTouches", 0.025, 0.0, 0.2);
    public static final ModConfigSpec.DoubleValue CONSERVATION = B.comment("Part de la vitesse gardee au lacher (1 = toute).").defineInRange("vitesseAuLacher", 1.0, 0.0, 2.0);
    public static final ModConfigSpec.DoubleValue SAUT = B.comment("Saut au lacher : vitesse vers le haut ajoutee quand on lache la corde, en blocs par tick (0,42 = un saut normal, 0 = pas de saut).").defineInRange("sautAuLacher", 0.42, 0.0, 1.5);
    public static final ModConfigSpec.DoubleValue RAIDEUR = B.comment("Force de rappel quand la corde est trop tendue (0 a 1).").defineInRange("raideurCorde", 0.5, 0.05, 1.0);
    public static final ModConfigSpec.DoubleValue CORDE_MIN = B.comment("Longueur minimale de corde, en blocs.").defineInRange("cordeMin", 2.0, 1.0, 10.0);
    public static final ModConfigSpec.DoubleValue TREUIL = B.comment("Vitesse du treuil : corde rembobinee par tick quand on maintient la touche de saut, en blocs (0,3 = 6 blocs/s).").defineInRange("vitesseTreuil", 0.3, 0.0, 2.0);
    public static final ModConfigSpec.DoubleValue PAS_MOLETTE = B.comment("Blocs de corde gagnes ou perdus par cran de molette.").defineInRange("pasMolette", 1.0, 0.25, 4.0);

    public static final ModConfigSpec.IntValue VOILE_RESERVE = B.comment("Voile des Profondeurs : duree de respiration sous l'eau, en secondes.").defineInRange("voileReserveSecondes", 40, 5, 600);
    public static final ModConfigSpec.IntValue VOILE_RECHARGE = B.comment("Voile des Profondeurs : temps pour le recharger entierement a la surface, en secondes.").defineInRange("voileRechargeSecondes", 12, 1, 600);
    public static final ModConfigSpec.IntValue VOILE_ALERTE = B.comment("Voile des Profondeurs : il avertit (message et son) quand il lui reste moins de ce nombre de secondes.").defineInRange("voileAlerteSecondes", 10, 1, 60);

    public static final ModConfigSpec.IntValue PARAVOILE_ENDURANCE = B.comment("Paravoile : duree de vol, en secondes (la jauge se vide en vol).").defineInRange("paravoileEnduranceSecondes", 10, 3, 300);
    public static final ModConfigSpec.IntValue PARAVOILE_RECHARGE = B.comment("Paravoile : temps pour recharger la jauge entierement, au sol ou dans l'eau, en secondes.").defineInRange("paravoileRechargeSecondes", 6, 1, 300);
    public static final ModConfigSpec.DoubleValue PARAVOILE_VITESSE = B.comment("Paravoile : vitesse de vol en avant, en blocs par tick (0,36 = 7 blocs/s).").defineInRange("paravoileVitesse", 0.36, 0.1, 1.0);
    public static final ModConfigSpec.DoubleValue PARAVOILE_DESCENTE = B.comment("Paravoile : vitesse de descente a plat, en blocs par tick (0,09 = 1,8 bloc/s).").defineInRange("paravoileDescente", 0.09, 0.0, 0.5);

    public static final ModConfigSpec SPEC = B.build();

    private Reglages() {
    }
}
