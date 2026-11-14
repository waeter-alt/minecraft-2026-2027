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

    public static final ModConfigSpec.BooleanValue VOILE_VISION_NOCTURNE = B.comment("Voile des Profondeurs : donne la vision nocturne tant qu'il est porte.").define("voileVisionNocturne", true);
    public static final ModConfigSpec.BooleanValue VOILE_NAGE_RAPIDE = B.comment("Voile des Profondeurs : donne la nage rapide (grace du dauphin) tant qu'il est porte.").define("voileNageRapide", true);

    public static final ModConfigSpec.IntValue PARAVOILE_ENDURANCE = B.comment("Paravoile : duree de vol, en secondes (la jauge se vide en vol).").defineInRange("paravoileEnduranceSecondes", 10, 3, 300);
    public static final ModConfigSpec.IntValue PARAVOILE_RECHARGE = B.comment("Paravoile : temps pour recharger la jauge entierement, au sol ou dans l'eau, en secondes.").defineInRange("paravoileRechargeSecondes", 6, 1, 300);
    public static final ModConfigSpec.DoubleValue PARAVOILE_VITESSE = B.comment("Paravoile : vitesse de vol en avant, en blocs par tick (0,36 = 7 blocs/s).").defineInRange("paravoileVitesse", 0.36, 0.1, 1.0);
    public static final ModConfigSpec.DoubleValue PARAVOILE_DESCENTE = B.comment("Paravoile : vitesse de descente a plat, en blocs par tick (0,09 = 1,8 bloc/s).").defineInRange("paravoileDescente", 0.09, 0.0, 0.5);

    public static final ModConfigSpec.DoubleValue SERAPHIN_VIE = B.comment("Seraphin : points de vie du boss.").defineInRange("seraphinVie", 300.0, 20.0, 5000.0);
    public static final ModConfigSpec.DoubleValue RAYON_DEGATS = B.comment("Seraphin : degats du rayon de l'oeil.").defineInRange("seraphinRayonDegats", 14.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue PLUME_DEGATS = B.comment("Seraphin : degats d'une plume.").defineInRange("seraphinPlumeDegats", 7.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue BALAYAGE_DEGATS = B.comment("Seraphin : degats du balayage d'ailes.").defineInRange("seraphinBalayageDegats", 10.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue AILES_FERMEES = B.comment("Seraphin : part des degats recus quand ses ailes sont fermees (1 = pas de reduction, l'oeil ouvert prend toujours tout).").defineInRange("seraphinAilesFermees", 0.3, 0.0, 1.0);
    public static final ModConfigSpec.IntValue OEIL_OUVERT = B.comment("Seraphin : duree pendant laquelle l'oeil reste ouvert apres le tir du rayon, en ticks (20 = 1 s) : c'est la fenetre ou il prend tous les degats.").defineInRange("seraphinOeilOuvert", 100, 10, 600);
    public static final ModConfigSpec.IntValue RAYON_CHARGE = B.comment("Seraphin : duree d'avertissement avant le rayon, en ticks (20 = 1 s).").defineInRange("seraphinRayonCharge", 30, 5, 200);

    public static final ModConfigSpec.DoubleValue GOLEM_VIE = B.comment("Gardien des Racines : points de vie du boss.").defineInRange("golemVie", 240.0, 20.0, 1024.0);
    public static final ModConfigSpec.DoubleValue GOLEM_FACTEUR_POINT = B.comment("Gardien : multiplicateur des degats d'une fleche sur un point faible ouvert.").defineInRange("golemFacteurPoint", 3.0, 0.1, 50.0);
    public static final ModConfigSpec.DoubleValue GOLEM_FACTEUR_CORPS = B.comment("Gardien : part des degats recus sur le reste du corps (0,15 = 15 %).").defineInRange("golemFacteurCorps", 0.15, 0.0, 1.0);
    public static final ModConfigSpec.IntValue GOLEM_OUVERTURE = B.comment("Gardien : duree d'ouverture d'un point faible, en ticks (20 = 1 s).").defineInRange("golemOuverture", 160, 20, 1200);
    public static final ModConfigSpec.IntValue GOLEM_DEMI_TOUR = B.comment("Gardien : temps qu'il met a faire un demi-tour vers le joueur, en ticks.").defineInRange("golemDemiTour", 300, 10, 1200);
    public static final ModConfigSpec.DoubleValue GRAINE_DEGATS = B.comment("Gardien : degats de l'explosion d'une graine.").defineInRange("graineDegats", 10.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue GRAINE_ZONE = B.comment("Gardien : rayon de l'explosion d'une graine, en blocs.").defineInRange("graineZone", 3.0, 0.5, 16.0);
    public static final ModConfigSpec.IntValue GRAINE_CADENCE = B.comment("Gardien : delai entre deux graines, en ticks.").defineInRange("graineCadence", 70, 10, 600);

    public static final ModConfigSpec.DoubleValue LEVIATHAN_VIE = B.comment("Leviathan : points de vie du boss.").defineInRange("leviathanVie", 150.0, 20.0, 1024.0);
    public static final ModConfigSpec.DoubleValue LEVIATHAN_FACTEUR_PERLE = B.comment("Leviathan : multiplicateur des degats sur la perle ouverte.").defineInRange("leviathanFacteurPerle", 3.0, 0.1, 50.0);
    public static final ModConfigSpec.DoubleValue LEVIATHAN_FACTEUR_CORPS = B.comment("Leviathan : part des degats recus sur le corps (0,15 = 15 %).").defineInRange("leviathanFacteurCorps", 0.15, 0.0, 1.0);
    public static final ModConfigSpec.IntValue LEVIATHAN_PERLE = B.comment("Leviathan : duree pendant laquelle la perle reste exposee apres le rayon, en ticks (20 = 1 s).").defineInRange("leviathanPerle", 120, 20, 1200);
    public static final ModConfigSpec.DoubleValue MORSURE_DEGATS = B.comment("Leviathan : degats de la morsure.").defineInRange("leviathanMorsure", 8.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue RAYON_LEVIATHAN_DEGATS = B.comment("Leviathan : degats du rayon de l'oeil.").defineInRange("leviathanRayon", 12.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue QUEUE_DEGATS = B.comment("Leviathan : degats du coup de queue.").defineInRange("leviathanQueue", 6.0, 0.0, 100.0);
    public static final ModConfigSpec.IntValue RAYON_LEVIATHAN_CHARGE = B.comment("Leviathan : duree d'avertissement avant le rayon, en ticks.").defineInRange("leviathanRayonCharge", 40, 10, 200);

    public static final ModConfigSpec SPEC = B.build();

    private Reglages() {
    }
}
