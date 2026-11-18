// [claude-build] Tous les reglages du grappin, au meme endroit : fichier config/grappin-common.toml (modifiable sans recompiler).
package fr.atelier.grappin;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Reglages {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue PORTEE = B.comment("Portee maximale du tir, en blocs.").defineInRange("portee", 30.0, 4.0, 64.0);
    public static final ModConfigSpec.DoubleValue VITESSE_CROCHET = B.comment("Vitesse du crochet en vol, en blocs par tick.").defineInRange("vitesseCrochet", 2.5, 0.5, 6.0);
    public static final ModConfigSpec.DoubleValue AIMANT = B.comment("Le crochet s'accroche a un point d'ancrage qu'il frole a moins de cette distance, en blocs (0 = il faut le toucher). Le grappin 3 vise un point : 1 bloc suffit.").defineInRange("aimant", 1.0, 0.0, 4.0);
    public static final ModConfigSpec.IntValue RECHARGE = B.comment("Temps de recharge apres un tir, en ticks (20 = 1 s).").defineInRange("recharge", 6, 0, 200);
    public static final ModConfigSpec.DoubleValue VITESSE_TRACTION = B.comment("Grappin 3 : vitesse a laquelle le crochet vous tire vers le point vise, en blocs par tick (0,75 = 15 blocs/s).").defineInRange("vitesseTraction", 0.75, 0.2, 3.0);
    public static final ModConfigSpec.DoubleValue ACCELERATION = B.comment("Grappin 3 : gain de vitesse par tick au depart de la traction, en blocs par tick.").defineInRange("accelerationTraction", 0.15, 0.02, 2.0);
    public static final ModConfigSpec.DoubleValue ARRIVEE = B.comment("Grappin 3 : distance au point d'ancrage (du centre du joueur a l'ancrage) a laquelle la traction s'arrete et le crochet se detache, en blocs.").defineInRange("distanceArrivee", 1.2, 0.6, 4.0);
    public static final ModConfigSpec.DoubleValue CONSERVATION = B.comment("Si on lache en cours de route avec ESPACE : part de la vitesse gardee (1 = toute).").defineInRange("vitesseAuLacher", 1.0, 0.0, 2.0);
    public static final ModConfigSpec.DoubleValue SAUT = B.comment("Si on lache en cours de route avec ESPACE : saut ajoute, en blocs par tick (0,42 = un saut normal, 0 = pas de saut).").defineInRange("sautAuLacher", 0.42, 0.0, 1.5);

    // Les vitesses de nage sont lues chez le joueur (comme la paravoile) : c'est le fichier du client qui compte pour elles, pas celui du serveur.
    public static final ModConfigSpec.DoubleValue CAPE_VITESSE = B.comment("Cape lestee : vitesse de nage rapide, en blocs par tick (0,455 = 9,1 blocs/s).").defineInRange("capeVitesse", 0.455, 0.05, 1.5);
    public static final ModConfigSpec.DoubleValue CAPE_VITESSE_SPRINT = B.comment("Cape lestee : vitesse de nage rapide avec la touche de sprint, en blocs par tick (0,715 = 14,3 blocs/s).").defineInRange("capeVitesseSprint", 0.715, 0.05, 2.0);
    public static final ModConfigSpec.DoubleValue CAPE_INERTIE = B.comment("Cape lestee : part de l'ecart a la vitesse voulue rattrapee chaque tick (petit = plus d'inertie, comme des elytres ; 1 = aucune).").defineInRange("capeAcceleration", 0.12, 0.02, 1.0);
    public static final ModConfigSpec.DoubleValue CAPE_SAUT_LESTE = B.comment("Cape lestee, mode leste : elan d'un saut depuis le fond, en blocs par tick (0,5 = environ un saut terrestre).").defineInRange("capeSautLeste", 0.5, 0.0, 1.5);
    public static final ModConfigSpec.DoubleValue TOURBILLON_PORTEE = B.comment("Cape lestee : portee de l'attaque tourbillon autour du joueur, en blocs (une epee : 3).").defineInRange("tourbillonPortee", 3.5, 1.0, 8.0);
    public static final ModConfigSpec.DoubleValue TOURBILLON_DEGATS = B.comment("Cape lestee : degats de l'attaque tourbillon (2 = un coeur).").defineInRange("tourbillonDegats", 6.0, 0.0, 40.0);
    public static final ModConfigSpec.IntValue TOURBILLON_RECHARGE = B.comment("Cape lestee : temps de recharge de l'attaque tourbillon, en ticks (20 = 1 s).").defineInRange("tourbillonRecharge", 16, 2, 200);
    public static final ModConfigSpec.DoubleValue TOURBILLON_ELAN = B.comment("Cape lestee : elan vers l'avant donne par le tourbillon, en blocs par tick (0 = aucun).").defineInRange("tourbillonElan", 0.8, 0.0, 2.0);
    public static final ModConfigSpec.DoubleValue TOURBILLON_PROJECTION = B.comment("Cape lestee : tourbillon pres de la surface en visant vers le haut = projection hors de l'eau. Vitesse verticale au depart, en blocs par tick (1,2 = environ 8 blocs de haut ; 0 = pas de projection, simple elan).").defineInRange("tourbillonProjection", 1.2, 0.0, 3.0);
    public static final ModConfigSpec.DoubleValue TOURBILLON_PROJECTION_PORTEE = B.comment("Cape lestee : vitesse horizontale de la projection vers l'avant du regard, en blocs par tick (1,0 = environ 10 blocs avant de retomber au niveau de l'eau ; reduite si on vise presque a la verticale).").defineInRange("tourbillonProjectionPortee", 1.0, 0.0, 4.0);

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
    public static final ModConfigSpec.IntValue GOLEM_OUVERTURE = B.comment("Gardien : duree d'ouverture d'un point faible, en ticks (20 = 1 s).").defineInRange("golemOuverture", 400, 20, 1200);
    public static final ModConfigSpec.IntValue GOLEM_DEMI_TOUR = B.comment("Gardien : temps qu'il met a faire un demi-tour vers le joueur, en ticks.").defineInRange("golemDemiTour", 300, 10, 1200);
    public static final ModConfigSpec.DoubleValue RODEUR_VIE = B.comment("Rodeur des Racines (mini-boss du sous-sol 1) : points de vie.").defineInRange("rodeurVie", 150.0, 10.0, 1024.0);
    public static final ModConfigSpec.DoubleValue RODEUR_MORSURE = B.comment("Rodeur : degats d une morsure ou d une charge qui touche.").defineInRange("rodeurMorsure", 8.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue RODEUR_VITESSE = B.comment("Rodeur : vitesse de marche.").defineInRange("rodeurVitesse", 0.3, 0.05, 1.0);
    public static final ModConfigSpec.DoubleValue RODEUR_VITESSE_CHARGE = B.comment("Rodeur : vitesse de la charge, en blocs par tick.").defineInRange("rodeurVitesseCharge", 0.9, 0.1, 3.0);
    public static final ModConfigSpec.DoubleValue RODEUR_PORTEE_CHARGE = B.comment("Rodeur : longueur maximale d une charge, en blocs.").defineInRange("rodeurPorteeCharge", 24.0, 4.0, 64.0);
    public static final ModConfigSpec.IntValue RODEUR_GRATTAGE = B.comment("Rodeur : duree du grattage avant la charge, en ticks (le temps de la voir venir).").defineInRange("rodeurGrattage", 20, 5, 100);
    public static final ModConfigSpec.IntValue RODEUR_SONNE = B.comment("Rodeur : duree pendant laquelle il reste sonne apres avoir percute un mur, en ticks.").defineInRange("rodeurSonne", 60, 10, 400);
    public static final ModConfigSpec.IntValue RODEUR_DELAI_CHARGE = B.comment("Rodeur : temps minimal entre deux charges, en ticks.").defineInRange("rodeurDelaiCharge", 120, 20, 1200);
    public static final ModConfigSpec.DoubleValue RODEUR_FACTEUR_SONNE = B.comment("Rodeur : multiplicateur des degats recus quand il est sonne.").defineInRange("rodeurFacteurSonne", 2.0, 1.0, 10.0);
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
