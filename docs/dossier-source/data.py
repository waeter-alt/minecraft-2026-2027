"""Feature catalogue for the Kodistan 2026 dossier.

faisa: 1 Facile, 2 Moyen, 3 Difficile, 4 Très difficile
risque: Faible / Moyen / Élevé
impact: Cœur / Fort / Moyen / Confort
charge: sessions (~3 h de travail en binôme)
prio: P0 socle de lancement, P1 livré pendant l'événement, P2 bonus, P3 en suspens
"""

DOMAINS = [
    ("A", "Infrastructure", "gears", "Les fondations invisibles : version, launcher, serveur, socle du mod."),
    ("B", "Économie", "coins-pile", "Couronnes, Influence, matériaux, banque : ce qui fait tourner le royaume."),
    ("C", "Capitale & Hôtel des Ventes", "castle", "La citadelle impériale et sa ville basse, épicentre du serveur."),
    ("D", "Empire & Politique", "crown", "Le trône, la dîme, les primes, la révolte, la couronne."),
    ("E", "Colonies", "village", "MineColonies revu de fond en comble : bâtir sans construire."),
    ("F", "Routes & Caravanes", "caravan", "Le commerce qui crée la richesse, et les convois qu'on pille."),
    ("G", "Combat, Arène & Classes", "crossed-swords", "La règle de mort, l'arène tournante, les maîtres des compétences."),
    ("H", "Aventure", "dragon-head", "Donjons, groupes inter-factions, dragon, monstres d'élite."),
    ("I", "Bastions", "tower-flag", "Les points stratégiques à reprendre, à repenser."),
    ("J", "Rythme", "hourglass", "Le calendrier des quatorze soirées."),
]

F = []


def f(id_, nom, desc, faisa, risque, impact, charge, prio, outils, durs, contournement):
    F.append(dict(id=id_, dom=id_[0], nom=nom, desc=desc, faisa=faisa, risque=risque, impact=impact,
                  charge=charge, prio=prio, outils=outils, durs=durs, contournement=contournement))


# ---------------------------------------------------------------- A Infrastructure
f("A1", "Version & modpack de base",
  "Choisir la version Minecraft/NeoForge la plus récente bien supportée par MineColonies, Create et la série RPG, puis tout figer.",
  1, "Faible", "Cœur", 1, "P0",
  "Modrinth, CurseForge, NeoForge",
  "Un mod clé pas encore porté sur la dernière version.",
  "Rester en 1.21.1, l'écosystème éprouvé de 2025.")
f("A2", "Launcher (plan B)",
  "Prism Launcher portable préconfiguré : instance Kodistan, thème et fond maison, mises à jour automatiques, raccourci de connexion directe.",
  2, "Moyen", "Cœur", 2, "P0",
  "Prism Launcher, packwiz, GitHub Pages",
  "NeoForge + packwiz pas encore testé ; certains mods CurseForge bloquent le téléchargement par des outils tiers.",
  "Tester dès la semaine 1 ; remplacer le mod fautif ou demander l'accord de l'auteur. Plan A (launcher maison) si Mojang valide un jour.")
f("A3", "Habillage en jeu",
  "Écran de chargement, menu principal redessiné, bouton « Rejoindre Kodistan », titre et icône de fenêtre, pack de ressources.",
  1, "Faible", "Fort", 1.5, "P1",
  "FancyMenu, Drippy Loading Screen, pack de ressources",
  "La qualité graphique dépend des visuels fournis.",
  "Une DA simple et forte (typo, fonds, logo) ; un artiste ponctuel pour le logo.")
f("A4", "Serveur & hébergement",
  "Serveur dédié dimensionné pour Create + MineColonies + 10 joueurs, sauvegardes automatiques, suivi des performances.",
  2, "Moyen", "Cœur", 1, "P0",
  "Hébergeur (location-minecraft.fr en 2025), spark, Chunky",
  "Le TPS avec Create, MineColonies, les caravanes et Distant Horizons.",
  "Prégénérer la carte, profiler avant le lancement, limiter fermes et routes par joueur.")
f("A5", "Socle du mod Kodistan",
  "Projet NeoForge modulaire : stockage des données, synchronisation client/serveur, configs rechargeables à chaud.",
  2, "Faible", "Cœur", 2, "P0",
  "NeoForge MDK, Gradle, Java 21",
  "Aucun point bloquant : c'est la fondation de tout le reste.",
  "—")
f("A6", "Outils d'administration",
  "Réglages en direct, transfert de régence, correctifs d'urgence, journal des événements.",
  1, "Faible", "Fort", 1, "P0",
  "Mod Kodistan (commandes admin)",
  "Résister à la tentation de corriger le jeu « à la main » pendant la partie.",
  "Pouvoirs d'admin strictement séparés des pouvoirs de l'Empire.")
f("A7", "Tests automatisés & serveur de test",
  "GameTests NeoForge, serveur de test, colonie de test accélérée.",
  2, "Faible", "Fort", 1, "P0",
  "NeoForge GameTest, serveur local",
  "Ce qui se ressent en jeu ne se teste qu'en jouant.",
  "Deux ou trois sessions de test avec des potes avant le lancement.")

# ---------------------------------------------------------------- B Économie
f("B1", "Couronnes & paliers d'opulence",
  "Pièce frappée par l'Empire ; compression ×10 jusqu'au Trésor du dragon ; tas d'or qui grossissent ; les gros paliers ralentissent et font briller.",
  1, "Faible", "Cœur", 1.5, "P0",
  "Mod Kodistan, Blockbench",
  "Des modèles et textures à la hauteur de l'opulence promise.",
  "Textures simples d'abord, version artistique ensuite.")
f("B2", "Banque impériale",
  "Comptes sûrs avec frais ; encaissement des gains de paris, primes et contrats.",
  1, "Faible", "Cœur", 1, "P0",
  "Mod Kodistan",
  "—",
  "—")
f("B3", "Trésor de colonie & pillage encadré",
  "Chambre forte pillable : réserve inviolable, 20 % max, immunité 2 soirs, alarme, 5 % si hors ligne, butin lourd.",
  3, "Moyen", "Fort", 2, "P1",
  "Mod Kodistan",
  "L'équilibre entre l'intérêt du pillage et la frustration du volé.",
  "Pillage réservé aux conflits déclarés ; toutes les valeurs réglables à chaud.")
f("B4", "Influence",
  "Monnaie politique non échangeable : présence plafonnée par soirée, arène, donjons ; perdue en partie à la mort en PvP.",
  2, "Moyen", "Cœur", 1, "P0",
  "Mod Kodistan",
  "Le dosage des sources pour ne pas récompenser le temps passé.",
  "Plafonds réglables et journal des gains pour ajuster en cours de partie.")
f("B5", "Sabliers de l'Influence",
  "Score exact et sablier 3D qui évolue selon son remplissage, façon « points des maisons ».",
  2, "Faible", "Fort", 1.5, "P1",
  "Blockbench, BlockEntityRenderer",
  "Un modèle vraiment beau.",
  "Un artiste pour le modèle final ; le code est prêt avant.")
f("B6", "Matériaux & autels de ressources",
  "Cinq matériaux en caisses ; chaque colonie reçoit l'autel de son biome, améliorable ; coffre d'export pour les caravanes.",
  2, "Moyen", "Cœur", 2, "P0",
  "Mod Kodistan, datapacks",
  "La répartition biomes ↔ ressources n'est pas encore définie.",
  "Un tableau de répartition simple, des valeurs réglables.")
f("B7", "Bourse aux prix dynamiques",
  "Achat et vente aux PNJ, écart achat/vente, stock limité chaque soir, tableau des cours.",
  2, "Moyen", "Fort", 1.5, "P1",
  "Mod Kodistan",
  "Les courbes de prix : inflation ou effondrement.",
  "Formules simples bornées par un minimum et un maximum.")
f("B8", "Biens de luxe",
  "Épices, vin, soie… produits dans une ou deux régions seulement.",
  2, "Moyen", "Moyen", 1.5, "P3",
  "Mod Kodistan",
  "Pas encore décidé, ni dans le principe ni dans la forme.",
  "Ajoutable plus tard sans casser l'économie de base.")

# ---------------------------------------------------------------- C Capitale
f("C1", "Citadelle impériale",
  "Palais, salle du trône, Trésor visible, ville basse compacte et organique, Hôtel des Ventes, lits, colisée.",
  3, "Élevé", "Cœur", 5, "P0",
  "Génération de structures (Python → .nbt/.schem), WorldEdit, Structurize",
  "La qualité esthétique au bloc près : c'est ce que je fais le moins bien.",
  "Je génère la géométrie, la déco se peaufine à la main ; base communautaire sous licence possible. La capitale grandit par étapes pendant la partie.")
f("C2", "Sanctuaire & masques",
  "Zone sans combat ni vol ; masques retirés automatiquement à l'entrée (au sol si l'inventaire est plein).",
  1, "Faible", "Cœur", 0.5, "P0",
  "Mod Kodistan",
  "—",
  "—")
f("C3", "Marché des joueurs",
  "Objets uniques, 3 annonces max, délai entre annonces, séquestre, aperçu complet de l'objet.",
  2, "Faible", "Fort", 1.5, "P1",
  "Mod Kodistan (interface)",
  "Une interface claire.",
  "Version 1 sobre, enchères ensuite (C3b).")
f("C3b", "Enchères",
  "Mise aux enchères des pièces rares, jusqu'à la fin de la soirée.",
  1, "Faible", "Moyen", 0.5, "P2",
  "Mod Kodistan",
  "—",
  "—")
f("C4", "Boutiques, réparation & forges",
  "PNJ spécialisés (consommables, objets uniques très chers, équipement de classe), réparation de la durabilité, forges utilisables.",
  1, "Faible", "Cœur", 1, "P0",
  "Mod Kodistan, datapacks",
  "Le dosage des prix.",
  "Prix réglables à chaud.")
f("C5", "Plats à effets",
  "Plats MineColonies modifiés, avec des effets plus intéressants que la nourriture vanilla.",
  1, "Faible", "Moyen", 1, "P2",
  "Datapacks, mod Kodistan",
  "Dépend des objets de MineColonies.",
  "Créer nos propres plats si besoin.")
f("C6", "Alcôves vocales privées",
  "Entrer dans une alcôve place automatiquement dans un canal vocal isolé.",
  2, "Moyen", "Moyen", 1, "P2",
  "API de Simple Voice Chat",
  "Les capacités de l'API côté serveur restent à vérifier.",
  "Groupes vocaux manuels avec mot de passe.")
f("C7", "Vie de la ville basse",
  "Des habitants PNJ, de l'animation, une ville qui respire.",
  2, "Moyen", "Confort", 1, "P2",
  "MineColonies, mod Kodistan",
  "Les citoyens MineColonies sont liés à une colonie.",
  "Une colonie « Empire » dédiée, ou des PNJ décoratifs maison.")

# ---------------------------------------------------------------- D Empire
f("D1", "Trône, édits & pilote automatique",
  "Tableau de bord du trône : 1 édit choisi parmi 3 chaque soir, faveurs, sanctions ; décisions automatiques si l'Empire est absent.",
  2, "Moyen", "Cœur", 2, "P0",
  "Mod Kodistan (interface)",
  "Un catalogue d'édits équilibrés.",
  "Démarrer avec 10 à 15 édits simples, en ajouter pendant la partie.")
f("D2", "Dîme impériale",
  "Tous les 3 jours, escalade automatique jusqu'à la Grande Dîme, proportionnelle à la richesse, sanctions par paliers.",
  2, "Moyen", "Cœur", 1.5, "P0",
  "Mod Kodistan",
  "Pousser à la révolte sans dégoûter les joueurs.",
  "Courbe réglable à chaud, suivi des réactions soir après soir.")
f("D3", "Trésor impérial visible",
  "Le tas d'or qui grossit avec chaque dîme, la carotte de la révolte.",
  1, "Faible", "Fort", 0.5, "P1",
  "Mod Kodistan (réutilise B1)",
  "—",
  "—")
f("D4", "Pactes runiques",
  "Contrats appliqués par le mod ; rompre un pacte vaut la Marque du traître.",
  4, "Élevé", "Fort", 3, "P2",
  "Mod Kodistan",
  "Un éditeur de contrats libre est presque impossible à vérifier automatiquement.",
  "Un catalogue de clauses prédéfinies : non-agression, tribut, exclusivité de route, pas de prime.")
f("D5", "Primes",
  "Exécution, vol, pillage, protection ; traqueur ; frais de 20 %, immunité, gel hors ligne ; primes du système.",
  3, "Moyen", "Fort", 2.5, "P1",
  "Mod Kodistan",
  "Les abus : acharnement, triche entre amis.",
  "Frais, immunité, plafond par cible, délai minimum.")
f("D6", "Révolte & siège",
  "Pacte de rébellion, levée de l'Étendard, siège programmé 48 h plus tard, victoire, transfert de régence.",
  3, "Élevé", "Cœur", 3, "P1",
  "Mod Kodistan",
  "L'équilibre 3 contre 7, les performances pendant le siège, des règles de victoire limpides.",
  "Siège testé en interne, défenses PNJ réglables, arbitrage admin prévu.")
f("D7", "Guerre totale déclarée",
  "Case à cocher à l'hôtel de ville, message à tout le serveur, délai, sanctions, reconstruction à prix réduit, lettre de guerre.",
  3, "Moyen", "Fort", 2, "P1",
  "Mod Kodistan",
  "Remplacer la protection des colonies de MineColonies par la nôtre.",
  "Destruction limitée aux bâtiments construits à partir de plans : la reconstruction est facile.")
f("D8", "Succession & couronnement",
  "Condition de victoire, bénédiction impériale, cérémonie du jour 14.",
  2, "Élevé", "Cœur", 2, "P1",
  "Mod Kodistan",
  "La condition de victoire n'est pas encore définie.",
  "Décider tôt une version simple (Influence + bénédiction + conditions).")
f("D9", "Rôles secrets, parchemin, indices",
  "Revendications secrètes, parchemin d'héritage volable, indices dans le monde.",
  3, "Élevé", "Moyen", 4, "P3",
  "Mod Kodistan",
  "Lourd à concevoir et à équilibrer ; mis en suspens.",
  "Une version légère éventuelle après le lancement.")
f("D10", "Masques",
  "Anonymat hors de la capitale : pseudo caché, apparence neutre.",
  2, "Moyen", "Moyen", 1.5, "P2",
  "Mod Kodistan (client)",
  "Cacher le skin demande du code côté client.",
  "Ne cacher que le pseudo dans un premier temps.")

# ---------------------------------------------------------------- E Colonies
f("E1", "Chantier instantané",
  "Aperçu fantôme, paiement en Couronnes + caisses, construction animée bloc par bloc, terrain préparé, démolition remboursée.",
  3, "Moyen", "Cœur", 3, "P0",
  "Structurize (format des plans), mod Kodistan",
  "L'intégration avec les bâtiments MineColonies et leurs niveaux.",
  "S'appuyer sur l'outil de construction de Structurize ; on paie à la place du constructeur.")
f("E2", "Refonte de MineColonies",
  "Rendements multipliés, outils fournis, nourriture et bonheur simplifiés, logistique instantanée, travail de nuit, recherches allégées.",
  4, "Élevé", "Cœur", 5, "P0",
  "Datapacks (315 recherches, recettes), mixins, résolveur de requêtes",
  "Un code interne complexe et fragile aux mises à jour.",
  "Figer la version ; datapacks d'abord, mixins ciblés ensuite ; contourner la logistique plutôt que la réécrire.")
f("E3", "Murailles par poteaux",
  "Tracé qui épouse le relief, tours et portes placées automatiquement, trois niveaux.",
  3, "Moyen", "Fort", 2, "P1",
  "Moteur de tracé partagé avec les routes",
  "Les terrains accidentés.",
  "Réutiliser le moteur des routes (F1).")
f("E4", "Quartiers résidentiels & population",
  "Un bloc de quartier génère plusieurs maisons ; la population arrive toute seule (et par les caravanes).",
  2, "Moyen", "Fort", 2, "P1",
  "Mod Kodistan, MineColonies",
  "Le recrutement interne de MineColonies.",
  "Mixins sur le recrutement ; génération procédurale des quartiers.")
f("E5", "Style pack & plans",
  "Les plans des bâtiments dans la DA steampunk et runique.",
  2, "Moyen", "Cœur", 2, "P0",
  "Structurize, styles MineColonies",
  "Volume de travail artistique.",
  "Partir des styles MineColonies existants et les adapter.")
f("E6", "Fermes Create préfabriquées",
  "Niveau 1 autonome ; niveau 2 avec sortie convoyeur à relier à la main ; moteur runique intégré.",
  3, "Moyen", "Moyen", 2, "P2",
  "Create, Structurize, mod Kodistan",
  "Les pièces mobiles de Create doivent être assemblées après la pose.",
  "Plans sans pièces mobiles (scies, déployeurs).")
f("E7", "Raids en événements",
  "Raids barbares annoncés, dosés, avec butin.",
  1, "Faible", "Moyen", 0.5, "P2",
  "Config MineColonies, mod Kodistan",
  "—",
  "—")

# ---------------------------------------------------------------- F Routes
f("F1", "Routes organiques",
  "Bloc d'alliance ; tracé A* sur le relief, lissage, ponts ; construction progressive sous les yeux des joueurs.",
  3, "Moyen", "Fort", 3, "P1",
  "Mod Kodistan, prototype Python sur la carte 2025",
  "Zones pas encore générées, obstacles extrêmes.",
  "Prégénérer la carte ; distance maximale ; valider l'algorithme sur la carte de 2025.")
f("F2", "Caravanes",
  "Flux continu même hors ligne, visibles par tous sur la carte, cargaison issue du coffre d'export, bénéfice selon la distance.",
  4, "Élevé", "Fort", 4, "P1",
  "Mod Kodistan, carte Xaero",
  "Simuler des caravanes dans des zones non chargées ; trajet d'entités sur de longues distances.",
  "Simulation abstraite (position calculée), entités affichées seulement près des joueurs.")
f("F3", "Améliorations de route",
  "Chemin de terre → route pavée → voie runique.",
  2, "Faible", "Moyen", 1.5, "P2",
  "Mod Kodistan",
  "—",
  "—")
f("F3b", "Ligne de train Create",
  "Le niveau ultime de la route.",
  3, "Moyen", "Confort", 2, "P3",
  "Create",
  "Les trains Create sur de longues distances.",
  "Bonus seulement.")
f("F4", "Gardes & escortes",
  "Mercenaires corruptibles, bonus d'escorte pour les joueurs.",
  2, "Moyen", "Moyen", 1.5, "P2",
  "Mod Kodistan",
  "—",
  "—")
f("F5", "Dégradation à la rupture",
  "La route se dégrade aussitôt l'alliance rompue, de plus en plus, jusqu'à disparaître.",
  2, "Faible", "Fort", 0.5, "P1",
  "Mod Kodistan",
  "—",
  "—")
f("F6", "Pillage de caravanes",
  "Une attaque ne vole qu'une partie de la cargaison.",
  2, "Moyen", "Fort", 1, "P1",
  "Mod Kodistan",
  "—",
  "—")

# ---------------------------------------------------------------- G Combat
f("G1", "Règle de mort",
  "Jamais de perte d'équipement ; on perd l'argent porté, un peu d'Influence face à un joueur, et de la durabilité (rouge = stats réduites, jamais de casse).",
  2, "Faible", "Cœur", 1, "P0",
  "Mod Kodistan",
  "—",
  "—")
f("G2", "Zones sûres & PvP ouvert",
  "Sanctuaires protégés, PvP libre sur le reste de la carte.",
  1, "Faible", "Cœur", 0.5, "P0",
  "Mod Kodistan",
  "—",
  "—")
f("G3", "Arène tournante : système + 3 arènes",
  "Changement à minuit (ou au démarrage du soir) : nouveau biome, nouvelles règles.",
  2, "Moyen", "Cœur", 2.5, "P0",
  "Génération de structures, mod Kodistan",
  "Des règles amusantes et équilibrées.",
  "Livrer les arènes suivantes pendant la partie.")
f("G3b", "Arène tournante : 5 arènes suivantes",
  "Faille du Nether, toundra, tempête, fonderie steampunk, vide…",
  2, "Faible", "Fort", 2.5, "P1",
  "Génération de structures",
  "—",
  "Une nouvelle arène livrée tous les deux soirs.")
f("G4", "Duels publics & paris",
  "Lancés depuis l'Hôtel ; notification discrète ; pari mutuel en deux clics ; gains à l'Hôtel.",
  2, "Moyen", "Fort", 2, "P1",
  "Mod Kodistan (interface client)",
  "L'ergonomie de la notification.",
  "Version 1 avec commande et petite fenêtre.")
f("G5", "Influence de l'arène",
  "Plafond par soirée, seule la première victoire par adversaire compte, bonus contre plus fort que soi.",
  1, "Faible", "Fort", 0.5, "P1",
  "Mod Kodistan",
  "—",
  "—")
f("G6", "Champion & défi de l'Empire",
  "Titre de champion avec avantages réels ; défier le champion de l'Empire pour diviser sa dîme.",
  2, "Moyen", "Fort", 1, "P1",
  "Mod Kodistan",
  "—",
  "—")
f("G6b", "Autres enjeux de l'arène",
  "Cagnotte, mises consenties, guerres par procuration, classement final.",
  2, "Moyen", "Moyen", 1.5, "P2",
  "Mod Kodistan",
  "—",
  "—")
f("G7", "Classes & maîtres des compétences",
  "Un maître par classe dans la capitale, épreuves payantes, meilleures compétences, classe secondaire.",
  3, "Élevé", "Cœur", 4, "P1",
  "Série RPG / Spell Engine (données JSON), mod Kodistan",
  "Système final non décidé ; intégration à Spell Engine ; équilibrage PvP.",
  "Version 1 : classes RPG existantes + maîtres qui débloquent les sorts existants par quêtes ; sorts maison ensuite.")
f("G8", "Sorts & compétences maison",
  "Environ six compétences inédites.",
  2, "Moyen", "Moyen", 3, "P2",
  "Spell Engine (JSON), mod Kodistan",
  "Les effets vraiment nouveaux demandent du code.",
  "Commencer par des variantes de sorts existants.")
f("G9", "Équilibrage du combat",
  "Ajustements continus des classes et des armes.",
  3, "Élevé", "Fort", 2, "P1",
  "Configs de la série RPG",
  "Ne se calcule pas sur papier.",
  "Sessions de test, réglages à chaud.")

# ---------------------------------------------------------------- H Aventure
f("H1", "Donjons : système + 2 donjons",
  "Salles assemblées par le jeu, épreuves, butin, déblocage au fil des soirs.",
  2, "Moyen", "Cœur", 3, "P0",
  "Structures « jigsaw », génération Python, loot tables",
  "La qualité du level design.",
  "Salles modulaires réutilisables d'un donjon à l'autre.")
f("H1b", "Donjons : 4 donjons suivants",
  "Un nouveau donjon tous les deux ou trois soirs.",
  2, "Faible", "Fort", 4, "P1",
  "Idem",
  "—",
  "Livrés pendant la partie.")
f("H2", "LFG façon MMO",
  "Interface de groupe : activité, rôles recherchés, règle de partage du butin (dont « libre »).",
  2, "Moyen", "Fort", 1.5, "P1",
  "Mod Kodistan (interface)",
  "Une interface agréable.",
  "Version 1 sobre.")
f("H3", "Dragon de l'Ender",
  "Réapparition régulière, calibré pour 3 à 4 joueurs, part selon la contribution, quêtes ultimes.",
  2, "Moyen", "Fort", 1.5, "P1",
  "Mod Kodistan",
  "Gérer l'End et la réapparition.",
  "Réapparition par commande programmée.")
f("H4", "Monstres d'élite",
  "Seuls les monstres d'élite et de donjon rapportent des Couronnes : pas de fermes à mobs.",
  2, "Faible", "Moyen", 1.5, "P1",
  "Mod Kodistan",
  "—",
  "—")

# ---------------------------------------------------------------- I Bastions
f("I1", "Bastions repensés",
  "Effets stratégiques, fenêtres de vulnérabilité, entretien, fortification.",
  3, "Moyen", "Moyen", 3, "P3",
  "Mod Kodistan",
  "Le concept est à revoir.",
  "Retravailler après le lancement.")
f("I2", "Failles éphémères",
  "Une faille s'ouvre chaque soir quelque part sur la carte ; la tenir rapporte une récompense unique.",
  2, "Faible", "Moyen", 1, "P2",
  "Mod Kodistan",
  "—",
  "—")

# ---------------------------------------------------------------- J Rythme
f("J1", "Calendrier des 14 soirées",
  "Déblocages programmés, annonces, dîmes, finale.",
  1, "Faible", "Cœur", 1, "P0",
  "Mod Kodistan",
  "—",
  "—")


PRIO = {
    "P0": ("Socle", "Doit être là au lancement"),
    "P1": ("En partie", "Livré pendant l'événement"),
    "P2": ("Bonus", "Si le temps le permet"),
    "P3": ("Suspens", "Décision à prendre"),
}
FAISA = {1: "Facile", 2: "Moyen", 3: "Difficile", 4: "Très difficile"}

RISKS = [
    ("Périmètre trop large pour le temps disponible", "Élevée", "Critique",
     "Tri P0–P3, tranche verticale pour mesurer la vitesse, livraisons pendant l'événement."),
    ("Refonte de MineColonies fragile", "Moyenne", "Fort",
     "Version figée, datapacks d'abord, colonie de test accélérée."),
    ("Caravanes dans des zones non chargées", "Moyenne", "Fort",
     "Simulation abstraite, entités visibles seulement près des joueurs."),
    ("Performances du serveur", "Moyenne", "Fort",
     "Carte prégénérée, profilage, limites de fermes et de routes par joueur."),
    ("Qualité visuelle (builds, modèles)", "Élevée", "Moyen",
     "Styles MineColonies existants, schematics sous licence, artiste ponctuel."),
    ("Équilibrage de l'économie et du PvP", "Élevée", "Fort",
     "Tout réglable à chaud, journaux, sessions de test avec des potes."),
    ("Condition de victoire non définie", "Moyenne", "Critique",
     "La décider avant la fin de la phase 1."),
    ("Mods CurseForge non distribuables via packwiz", "Moyenne", "Moyen",
     "Test en semaine 1, remplacement du mod si besoin."),
    ("Disponibilité de Duncan pour tester", "Moyenne", "Fort",
     "Tests automatisés, sessions planifiées, Claude avance en autonomie."),
    ("Sentiment d'injustice envers l'Empire", "Moyenne", "Moyen",
     "Règles publiques, pilote automatique, pouvoirs d'admin séparés."),
]

DECISIONS = [
    ("Nom du mode", "Titre de travail : « La Couronne Vacante »", "Avant l'habillage (A3)"),
    ("Date de lancement", "19 déc., 26 déc. ou 2 janv.", "16 nov., après calibrage"),
    ("Version Minecraft / NeoForge", "La plus récente avec le meilleur support des mods", "Semaine 1"),
    ("Répartition biomes ↔ ressources", "5 matériaux à placer", "Avant B6 (mi-novembre)"),
    ("Condition de victoire de la Succession", "Influence, bénédiction, conditions ?", "Fin de la phase 1"),
    ("Système de classes final", "Hybride, verrouillé ou dynamique", "Avant G7"),
    ("Biens de luxe", "Oui / non, et sous quelle forme", "Après le lancement"),
    ("Rachat de prime", "Profite à l'Hôtel ou au commanditaire", "Avant D5"),
    ("Rupture d'alliance", "Au-delà de la route qui se dégrade", "Avant F1"),
    ("Rôles secrets, parchemin, indices", "En suspens", "Après le lancement"),
    ("Bastions", "À repenser", "Après le lancement"),
    ("Hébergeur & offre serveur", "Mémoire, CPU, prix", "Avant la phase 3"),
    ("Taille de l'équipe Empire", "Selon le nombre d'inscrits", "À la clôture des inscriptions"),
]
