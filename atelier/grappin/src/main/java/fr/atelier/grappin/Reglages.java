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

    // Les vitesses de nage sont lues chez le joueur (comme la paravoile) : c'est le fichier du client qui compte pour elles, pas celui du serveur.
    public static final ModConfigSpec.DoubleValue CAPE_VITESSE = B.comment("Cape lestee : vitesse de nage rapide, en blocs par tick (0,455 = 9,1 blocs/s).").defineInRange("capeVitesse", 0.455, 0.05, 1.5);
    public static final ModConfigSpec.DoubleValue CAPE_VITESSE_SPRINT = B.comment("Cape lestee : vitesse de nage rapide avec la touche de sprint, en blocs par tick (0,715 = 14,3 blocs/s).").defineInRange("capeVitesseSprint", 0.715, 0.05, 2.0);
    public static final ModConfigSpec.DoubleValue CAPE_INERTIE = B.comment("Cape lestee : part de l'ecart a la vitesse voulue rattrapee chaque tick (petit = plus d'inertie, comme des elytres ; 1 = aucune).").defineInRange("capeAcceleration", 0.12, 0.02, 1.0);
    public static final ModConfigSpec.DoubleValue CAPE_SAUT_LESTE = B.comment("Cape lestee, mode leste : elan d'un saut depuis le fond, en blocs par tick (0,5 = environ un saut terrestre).").defineInRange("capeSautLeste", 0.5, 0.0, 1.5);
    public static final ModConfigSpec.DoubleValue TOURBILLON_PORTEE = B.comment("Cape lestee : portee de l'attaque tourbillon autour du joueur, en blocs (une epee : 3).").defineInRange("tourbillonPortee", 3.5, 1.0, 8.0);
    public static final ModConfigSpec.DoubleValue TOURBILLON_DEGATS = B.comment("Cape lestee : degats de l'attaque tourbillon (2 = un coeur).").defineInRange("tourbillonDegats", 6.0, 0.0, 40.0);
    public static final ModConfigSpec.IntValue TOURBILLON_RECHARGE = B.comment("Cape lestee : temps de recharge de l'attaque tourbillon, en ticks (20 = 1 s).").defineInRange("tourbillonRecharge", 16, 2, 200);
    public static final ModConfigSpec.DoubleValue TOURBILLON_ELAN = B.comment("Cape lestee : elan vers l'avant donne par le tourbillon, en blocs par tick (0 = aucun).").defineInRange("tourbillonElan", 0.8, 0.0, 2.0);
    public static final ModConfigSpec.DoubleValue TOURBILLON_PROJECTION = B.comment("Cape lestee : tourbillon pres de la surface en visant vers le haut = projection hors de l'eau. Vitesse verticale au depart, en blocs par tick (1,8 = environ 16 blocs de haut ; 0 = pas de projection, simple elan).").defineInRange("tourbillonProjection", 1.8, 0.0, 3.0);
    public static final ModConfigSpec.DoubleValue TOURBILLON_PROJECTION_PORTEE = B.comment("Cape lestee : vitesse horizontale de la projection vers l'avant du regard, en blocs par tick (2,0 = environ 22 blocs avant de retomber au niveau de l'eau ; reduite si on vise presque a la verticale).").defineInRange("tourbillonProjectionPortee", 2.0, 0.0, 4.0);

    public static final ModConfigSpec.IntValue PARAVOILE_ENDURANCE = B.comment("Paravoile : duree de vol, en secondes (la jauge se vide en vol).").defineInRange("paravoileEnduranceSecondes", 10, 3, 300);
    public static final ModConfigSpec.IntValue PARAVOILE_RECHARGE = B.comment("Paravoile : temps pour recharger la jauge entierement, au sol ou dans l'eau, en secondes.").defineInRange("paravoileRechargeSecondes", 6, 1, 300);
    public static final ModConfigSpec.DoubleValue PARAVOILE_VITESSE = B.comment("Paravoile : vitesse de vol en avant, en blocs par tick (0,36 = 7 blocs/s).").defineInRange("paravoileVitesse", 0.36, 0.1, 1.0);
    public static final ModConfigSpec.DoubleValue PARAVOILE_DESCENTE = B.comment("Paravoile : vitesse de descente a plat, en blocs par tick (0,09 = 1,8 bloc/s).").defineInRange("paravoileDescente", 0.09, 0.0, 0.5);

    public static final ModConfigSpec SPEC = B.build();

    private Reglages() {
    }
}
