<!-- [claude-build] -->
# MANIFEST

Liste de chaque fichier généré par Claude (marque [claude-build]).

## Fichiers du projet

| Fichier | Date | Rôle |
|---|---|---|
| CLAUDE.md | 2026-11-03 | Règles de travail et organisation du projet |
| MANIFEST.md | 2026-11-03 | Ce registre |
| launcher/ (contenu du zip) | 2026-11-03 | Prism Launcher 11.1.1 portable (MSVC), décompressé depuis le zip officiel GitHub — fichiers tiers, non modifiés |
| launcher/instances/Build-1.21.1/instance.cfg | 2026-11-03 | Instance Prism vanilla « Build-1.21.1 » |
| launcher/instances/Build-1.21.1/mmc-pack.json | 2026-11-03 | Version de l'instance : Minecraft 1.21.1, sans chargeur de mods |
| launcher/prismlauncher.cfg (modifié, fichier de Prism) | 2026-11-03 | Ajout de AutomaticJavaDownload=true et AutomaticJavaSwitch=true (fait à la demande de l'utilisateur) |
| launcher/instances/Build-1.21.1/minecraft/options.txt (modifié, fichier de Minecraft) | 2026-11-03 | Réglages graphiques optimisés RTX 2070 SUPER / 1440p (à la demande de l'utilisateur). Original sauvegardé dans options.txt.avant-claude |
| launcher/instances/Build-1.21.1/minecraft/options.txt.avant-claude | 2026-11-03 | Copie des options d'origine (pour revenir en arrière) |
| launcher/instances/Build-1.21.1/instance.cfg (ajout) | 2026-11-03 | RAM 2–6 Go, fenêtre lancée maximisée |
| saves/Atelier/ (renommé depuis « Nouveau monde ») | 2026-11-03 | Monde de l'utilisateur, renommé à sa demande. level.dat : LevelName → « Atelier » ; original dans level.dat.avant-claude |
| saves/Atelier/level.dat (modifié) | 2026-11-03 | generate_features 1 → 0 (plus de villages/structures). Copie d'avant : level.dat.avant-sans-structures |
| sauvegardes/atelier-terrain-avant-sans-structures/ | 2026-11-03 | Terrain d'origine (region/, entities/, poi/ — 24 fichiers) retiré du monde pour le régénérer sans structures. Pour annuler : remettre ces 3 dossiers dans saves/Atelier/ |
| saves/Atelier/datapacks/atelier/pack.mcmeta | 2026-11-03 | Datapack « atelier », pack_format 48 |
| saves/Atelier/datapacks/atelier/data/atelier/structure/LISEZMOI.txt | 2026-11-03 | Dossier des structures générées (namespace atelier:) |
| sources/arene_feu.py | 2026-11-04 | Générateur de l'arène de feu (v1) |
| saves/Atelier/datapacks/atelier/data/atelier/structure/arene_feu.nbt | 2026-11-04 | Structure arène feu (copie datapack) |
| saves/Atelier/datapacks/atelier/data/atelier/function/arene_feu_effacer.mcfunction | 2026-11-04 | Remet le terrain plat sur l'emprise de l'arène (avant de reposer une nouvelle version) |
| exports/arene_feu.nbt | 2026-11-04 | Structure arène feu (à transmettre) |
| exports/arene_feu_plan.png | 2026-11-04 | Plan vu du dessus + souterrain (généré par le script) |
| exports/arene_feu_capture.png | 2026-11-04 | Capture en jeu de l'arène feu (copie de screenshots/2026-11-04_00.22.56.png) |
| sources/arene_forge.py | 2026-11-04 | Générateur de l'arène n°2 « La Forge » (v1) |
| saves/Atelier/datapacks/atelier/data/atelier/structure/arene_forge.nbt | 2026-11-04 | Structure arène forge (copie datapack) |
| saves/Atelier/datapacks/atelier/data/atelier/function/arene_forge_effacer.mcfunction | 2026-11-04 | Remet le terrain plat sur l'emprise de l'arène forge |
| exports/arene_forge.nbt | 2026-11-04 | Structure arène forge (à transmettre) |
| exports/arene_forge_plan.png | 2026-11-04 | Plan vu du dessus + souterrain |
| sources/forge_v2.py | 2026-11-04 | Générateur de l'arène « Forge v2 » (Ø252, 7 phases vérifiées) |
| saves/Atelier/datapacks/atelier/data/atelier/structure/forge_v2.nbt | 2026-11-04 | Structure forge_v2 (copie datapack) |
| saves/Atelier/datapacks/atelier/data/atelier/function/forge_v2_effacer.mcfunction | 2026-11-04 | Remet le terrain plat sur l'emprise de forge_v2 (2 /fill par couche) |
| exports/forge_v2.nbt | 2026-11-04 | Structure forge_v2 (à transmettre) |
| exports/forge_v2_plan.png | 2026-11-04 | Plan vu du dessus, salles cachées marquées |
| saves/Arene Feu/datapacks/atelier/pack.mcmeta | 2026-11-04 | Datapack « atelier » installé dans le nouveau monde Arene (pack_format 48) |
| saves/Arene Feu/datapacks/atelier/data/atelier/structure/forge_v2.nbt | 2026-11-04 | forge_v2 dans le monde Arene |
| saves/Arene Feu/datapacks/atelier/data/atelier/function/forge_v2_effacer.mcfunction | 2026-11-04 | Aplanit l'emprise de forge_v2 dans Arene (grès, air) — 2 /fill par couche |
| sources/forge_v2.py (option --demi) | 2026-11-04 | Même générateur, échelle 1/2 → forge_v2_demi (taille réelle vérifiée inchangée au bloc près) |
| exports/forge_v2_demi.nbt | 2026-11-04 | Demi-arène forge_v2 (Ø126, à transmettre) |
| exports/forge_v2_demi_plan.png | 2026-11-04 | Plan vu de dessus de la demi-arène |
| saves/Arene Feu/datapacks/atelier/data/atelier/structure/forge_v2_demi.nbt | 2026-11-04 | Demi-arène (copie datapack) |
| saves/Arene Feu/datapacks/atelier/data/atelier/function/forge_v2_demi_effacer.mcfunction | 2026-11-04 | Aplanit l'emprise de la demi-arène (1 /fill par couche) |
| salles_cachees_demi.md | 2026-11-04 | Salles cachées de la demi-arène (coordonnées des coffres, accès) |
| salles_cachees.md | 2026-11-04 (régénéré pour le monde Arene, centre X0 Z0, sol Y86) | Liste des 9 salles cachées de forge_v2 (coordonnées des coffres, accès) + résumé et écarts |
| exports/Arene_map.zip | 2026-11-05 | Copie complète de la map « Arene » pour l'ami (dossier `saves/Arene Feu` sans session.lock, renommé `Arene` dans le zip) : forge_v2 + forge_v2_demi + datapack |
| saves/Arene Feu/, saves/Arene Air/ | 2026-11-05 | Dossiers des mondes renommés à la demande de l'utilisateur (anciens noms : `Arene (1)`, `Nouveau monde`) |
| sources/verif_acces.py | 2026-11-05 | Contrôle d'accès à pied réutilisable (relit un .nbt et un fichier de cibles) |
| sources/col_vents.py, exports/col_vents.nbt, exports/col_vents_plan.png, exports/col_vents_cibles.json | 2026-11-05 | Premier essai de l'arène Air, abandonné et jamais posé |
| sources/quatre_vents.py | 2026-11-05 | Générateur du « Temple des Quatre Vents » (Ø252), remplacé par arene_air |
| exports/quatre_vents.nbt | 2026-11-05 | Structure de l'arène Air (à transmettre) |
| exports/quatre_vents_plan.png | 2026-11-05 | Plan vu de dessus, salles cachées marquées |
| exports/quatre_vents_cibles.json | 2026-11-05 | Liste des points à atteindre pour sources/verif_acces.py |
| salles_cachees_air.md | 2026-11-05 | Salles cachées, remontées, apparitions, résumé et écarts de l'arène Air (régénéré par sources/arene_air.py) |
| saves/Arene Air/datapacks/atelier/pack.mcmeta | 2026-11-05 | Datapack « atelier » du monde Arene Air (pack_format 48) |
| saves/Arene Air/datapacks/atelier/data/atelier/structure/quatre_vents.nbt | 2026-11-05 | Arène Air (copie datapack) |
| saves/Arene Air/datapacks/atelier/data/atelier/function/quatre_vents_effacer.mcfunction | 2026-11-05 | Aplanit l'emprise de l'arène Air (calcite, air) |
| sources/arene_air.py | 2026-11-05 | Générateur de l'arène Air suspendue (Ø192, 9 phases vérifiées, éclairage calculé) |
| sources/verif_remontees.py | 2026-11-05 | Teste chaque remontée seule et dessine la vue 3D de l'arène Air |
| exports/arene_air.nbt | 2026-11-05 | Structure de l'arène Air (à transmettre) |
| exports/arene_air_plan.png | 2026-11-05 | Plan vu de dessus : salles et remontées marquées |
| exports/arene_air_vue.png | 2026-11-05 | Vue 3D de l'arène Air |
| exports/arene_air_cibles.json | 2026-11-05 | Points à atteindre pour sources/verif_acces.py |
| saves/Arene Air/datapacks/atelier/data/atelier/structure/arene_air.nbt | 2026-11-05 | Arène Air (copie datapack) |
| saves/Arene Air/datapacks/atelier/data/atelier/function/arene_air_effacer.mcfunction | 2026-11-05 | Aplanit l'emprise de l'arène Air (calcite, air) |
| exports/Arene_Air_map.zip | 2026-11-05 | Copie complète de la map Arene Air pour l'ami (dossier `saves/Arene Air` sans session.lock) : arène Air + datapack |
| sources/arene_nature.py | 2026-11-05 | Générateur de l'arène Nature (Ø252, 7 phases vérifiées, éclairage calculé) |
| exports/arene_nature.nbt | 2026-11-05 | Structure de l'arène Nature (à transmettre) |
| exports/arene_nature_plan.png | 2026-11-05 | Plan vu de dessus, salles cachées marquées |
| exports/arene_nature_vue.png | 2026-11-05 | Vue 3D de l'arène Nature |
| exports/arene_nature_cibles.json | 2026-11-05 | Points à atteindre pour sources/verif_acces.py |
| salles_cachees_nature.md | 2026-11-05 | Salles cachées, apparitions, résumé et écarts de l'arène Nature |
| saves/Arene Nature/datapacks/atelier/pack.mcmeta | 2026-11-05 | Datapack « atelier » du monde Arene Nature (pack_format 48) |
| saves/Arene Nature/datapacks/atelier/data/atelier/structure/arene_nature.nbt | 2026-11-05 | Arène Nature (copie datapack) |
| saves/Arene Nature/datapacks/atelier/data/atelier/function/arene_nature_effacer.mcfunction | 2026-11-05 | Aplanit l'emprise de l'arène Nature (terre, herbe, air) |
| exports/Arene_Nature_map.zip | 2026-11-05 (refait après correction de la grotte) | Copie complète de la map Arene Nature pour l'ami (dossier `saves/Arene Nature` sans session.lock) : arène Nature + datapack |
| exports/Arene_Glace_map.zip | 2026-11-05 | Copie complète de la map Arene Glace pour l'ami (dossier `saves/Arene Glace` sans session.lock) : arène Glace + datapack |
| sources/arene_glace.py | 2026-11-05 | Générateur de l'arène Glace (Ø252, 7 phases vérifiées, éclairage calculé, aucune eau) |
| exports/arene_glace.nbt | 2026-11-05 | Structure de l'arène Glace (à transmettre) |
| exports/arene_glace_plan.png | 2026-11-05 | Plan vu de dessus : salles et pièges marqués |
| exports/arene_glace_vue.png | 2026-11-05 | Vue 3D de l'arène Glace |
| exports/arene_glace_cibles.json | 2026-11-05 | Points à atteindre pour sources/verif_acces.py |
| salles_cachees_glace.md | 2026-11-05 | Salles cachées, pièges, apparitions, résumé et écarts de l'arène Glace |
| saves/Arene Glace/datapacks/atelier/pack.mcmeta | 2026-11-05 | Datapack « atelier » du monde Arene Glace (pack_format 48) |
| saves/Arene Glace/datapacks/atelier/data/atelier/structure/arene_glace.nbt | 2026-11-05 | Arène Glace (copie datapack) |
| saves/Arene Glace/datapacks/atelier/data/atelier/function/arene_glace_effacer.mcfunction | 2026-11-05 | Aplanit l'emprise de l'arène Glace (glace compacte, neige, air) |
| sources/patch_grotte_nature.py | 2026-11-05 | Extrait les blocs qui diffèrent entre deux versions d'arene_nature.nbt, pour corriger une arène déjà posée |
| exports/arene_nature_grotte.nbt | 2026-11-05 | Correctif du sol de la grotte Nature (1 564 blocs) pour les arènes déjà posées ; inutile avec arene_nature.nbt à jour |
| saves/Arene Nature/datapacks/atelier/data/atelier/structure/arene_nature_grotte.nbt | 2026-11-05 | Correctif de la grotte (copie datapack) |
| sources/hub.py | 2026-11-05 | Générateur du hub « Carrefour des Éléments » et de ses 4 avenues ; écrit aussi les fonctions de pose |
| sources/verif_hub.py | 2026-11-05 | Reconstitue le monde final et vérifie à pied l'entrée dans chaque arène, sans sortie |
| exports/hub.nbt | 2026-11-05 | Structure du hub et des avenues |
| exports/hub_plan.png, exports/hub_vue.png | 2026-11-05 | Plan et vue 3D du hub |
| exports/hub_cibles.json | 2026-11-05 | Points à atteindre pour sources/verif_acces.py |
| exports/hub_tout.mcfunction, exports/hub_pose.mcfunction | 2026-11-05 | Fonctions qui posent le hub et les 4 arènes, ouvrent et ferment les portes |
| saves/Arene Hub/datapacks/atelier/ | 2026-11-05 | Datapack du monde Arene Hub : hub, forge_v2, arene_air, arene_nature, arene_glace, fonctions hub_tout et hub_pose |
| exports/Arene_Hub_map.zip | 2026-11-05 | Copie complète du monde final Arene Hub pour l'ami (dossier `saves/Arene Hub` sans session.lock) : hub, 4 arènes reliées, datapack |
| sources/arenes_mini.py | 2026-11-05 | Générateur des 4 arènes Ø104 (Feu, Air, Nature, Glace) pour Arene Hub |
| sources/hub_mini.py | 2026-11-05 | Installe les arènes Ø104 dans Arene Hub : fonctions d'effacement des grandes arènes et de pose |
| exports/mini_feu.nbt, mini_air.nbt, mini_nature.nbt, mini_glace.nbt | 2026-11-05 | Arènes Ø104 (104×62×104) |
| exports/mini_feu_cibles.json, mini_air_cibles.json, mini_nature_cibles.json, mini_glace_cibles.json | 2026-11-05 | Points à atteindre pour sources/verif_acces.py |
| exports/mini_vue.png | 2026-11-05 | Vue 3D des 4 arènes Ø104 |
| salles_cachees_hub.md | 2026-11-05 | Salles cachées, apparitions et pièges du monde Arene Hub version Ø104 |
| saves/Arene Hub/datapacks/atelier/data/atelier/function/hub_mini*.mcfunction | 2026-11-05 | Effacement des grandes arènes puis pose des arènes Ø104 |
| sources/patch_sol.py | 2026-11-05 | Extrait les blocs modifiés du hub et des arènes Ø104, pour corriger un monde posé sans toucher aux coffres |
| exports/sol_hub.nbt, sol_mini_feu.nbt, sol_mini_air.nbt, sol_mini_nature.nbt, sol_mini_glace.nbt | 2026-11-05 | Correctif du sol simplifié ; inutile avec les hub.nbt et mini_*.nbt à jour |
| saves/Arene Hub/datapacks/atelier/data/atelier/function/sol_doux*.mcfunction | 2026-11-05 | Pose du correctif de sol |
| exports/Arene_Hub_Mini_map.zip | 2026-11-05 | Copie complète du monde Arene Hub en version Ø104, sol simplifié (dossier `saves/Arene Hub` sans session.lock) |
| launcher/instances/Build-1.21.1/mmc-pack.json (modifié, fichier de Prism) | 2026-11-05 | Ajout de NeoForge 21.1.265, à la demande de l'utilisateur. Original dans mmc-pack.json.avant-mods |
| launcher/instances/Build-1.21.1/mmc-pack.json.avant-mods | 2026-11-05 | Copie de la config vanilla de l'instance (pour revenir en arrière) |
| launcher/instances/Build-1.21.1/minecraft/mods/create-1.21.1-6.0.10.jar | 2026-11-05 | Mod Create 6.0.10, depuis Modrinth, empreinte sha512 vérifiée (fichier tiers) |
| launcher/instances/Build-1.21.1/minecraft/mods/worldedit-mod-7.3.8.jar | 2026-11-05 | Mod WorldEdit 7.3.8, depuis Modrinth, empreinte sha512 vérifiée (fichier tiers) |
| sources/croissant.py | 2026-11-05 | Générateur de l'arène glace en croissant (Ø104) |
| exports/croissant.nbt | 2026-11-05 | Structure du croissant (à transmettre) |
| exports/croissant_plan.png, exports/croissant_vue.png | 2026-11-05 | Plan avec le chemin entrée-sortie, et vue 3D |
| exports/croissant_cibles.json | 2026-11-05 | Points à atteindre pour sources/verif_acces.py |
| arene_glace.md | 2026-11-05 | Entrée, sortie, pièges, sauts de la tour, résumé et écarts |
| saves/Nouveau monde/datapacks/atelier/ | 2026-11-05 | Datapack du monde du croissant : structure croissant et fonction croissant_effacer |
| sources/patch_croissant.py | 2026-11-05 | Extrait les blocs modifiés du croissant pour corriger l'arène déjà posée |
| exports/croissant_maj.nbt | 2026-11-05 | Correctif : rempart, porte est, pics et congères ; inutile avec croissant.nbt à jour |
| sources/feu2.py | 2026-11-05 | Générateur de l'arène Feu Ø104, 2e de la série des 7 |
| exports/feu2.nbt | 2026-11-05 | Structure de l'arène Feu Ø104 (à transmettre) |
| exports/feu2_vue.png | 2026-11-05 | Vue 3D de l'arène Feu Ø104 |
| exports/feu2_cibles.json | 2026-11-05 | Points à atteindre pour sources/verif_acces.py |
| arene_feu.md | 2026-11-05 | Cercle de feu, accès au souterrain, tours, salles, résumé et écarts |
| saves/Arene Feu/datapacks/atelier/ | 2026-11-05 | Datapack du monde Arene Feu : structure feu2 |
| sources/patch_structure.py | 2026-11-05 | Correctif générique : ne garde que les blocs modifiés entre deux versions d'une structure |
| exports/feu2_maj.nbt | 2026-11-05 | Correctif : tours délabrées et meurtrières de l'arène Feu ; inutile avec feu2.nbt à jour |
| sources/jungle.py | 2026-11-05 | Générateur de l'arène jungle suspendue, 3e de la série des 7 |
| exports/jungle.nbt | 2026-11-05 | Structure de l'arène jungle (à transmettre) |
| exports/jungle_vue.png | 2026-11-05 | Vue 3D de l'arène jungle |
| exports/jungle_cibles.json | 2026-11-05 | Points à atteindre pour sources/verif_acces.py |
| arene_jungle.md | 2026-11-05 | Hauteurs, arrivées, arbres, sauts, résumé et écarts |
| saves/Arene Jungle/datapacks/atelier/ | 2026-11-05 | Datapack du monde Arene Jungle : structure jungle |
| sources/piege.py | 2026-11-05 | Générateur de l'arène piège, 4e de la série des 7 |
| exports/piege.nbt | 2026-11-05 | Structure de l'arène piège ; demande NeoForge et Create |
| exports/piege_vue.png | 2026-11-05 | Vue 3D de l'arène piège |
| exports/piege_cibles.json | 2026-11-05 | Points à atteindre pour sources/verif_acces.py |
| arene_piege.md | 2026-11-05 | Liste des pièges, piège mortel, bouton de remise en état |
| saves/Arene Piege/datapacks/atelier/ | 2026-11-05 | Datapack du monde Arene Piege : structure piege |
| exports/piege_maj.nbt | 2026-11-05 | Correctif : minuteur du piège mortel et décor de l'arène piège ; inutile avec piege.nbt à jour |
| exports/piege_usine.nbt | 2026-11-05 | [claude-build] Correctif : usine Create de l'arène piège ; `/place template atelier:piege_usine -47 82 -47` ; inutile avec piege.nbt à jour |
| sources/abysses.py | 2026-11-06 | [claude-build] Générateur de l'arène Les Abysses, 5e de la série des 7 |
| sources/verif_air.py | 2026-11-06 | [claude-build] Contrôle de la distance à l'air dans une arène sous-marine |
| exports/abysses.nbt | 2026-11-06 | [claude-build] Structure de l'arène Les Abysses ; 104×56×104 ; demande NeoForge et Create ; `/place template atelier:abysses -52 68 -52` |
| exports/abysses_vue.png | 2026-11-06 | [claude-build] Vue 3D de l'arène Les Abysses, sans l'eau ni le verre |
| exports/abysses_cibles.json | 2026-11-06 | [claude-build] Points à atteindre pour sources/verif_acces.py |
| arene_abysses.md | 2026-11-06 | [claude-build] Points d'air, Cœur, conduites, apparitions |
| saves/Arene Abysses/datapacks/atelier/ | 2026-11-06 | [claude-build] Datapack du monde Arene Abysses : structure abysses |
| sources/tenebres.py | 2026-11-06 | [claude-build] Générateur de l'arène Les Ténèbres, 6e de la série des 7 |
| sources/verif_bruit.py | 2026-11-06 | [claude-build] Contrôle des capteurs, des tapis muets et de la lumière |
| exports/tenebres.nbt | 2026-11-06 | [claude-build] Structure de l'arène Les Ténèbres ; 104×46×104 ; `/place template atelier:tenebres -52 68 -52` |
| exports/tenebres_vue.png | 2026-11-06 | [claude-build] Vue 3D de l'arène Les Ténèbres, sans la voûte |
| exports/tenebres_cibles.json | 2026-11-06 | [claude-build] Points à atteindre pour sources/verif_acces.py |
| arene_tenebres.md | 2026-11-06 | [claude-build] Capteurs, sanctuaire, Gardien, apparitions |
| saves/Arene Tenebres/datapacks/atelier/ | 2026-11-06 | [claude-build] Datapack du monde Arene Tenebres : structure tenebres |
| sources/portes.py | 2026-11-06 | [claude-build] Générateur de l'arène Les Portes, 7e de la série |
| sources/verif_portes.py | 2026-11-06 | [claude-build] Contrôle des 8 paires de portails |
| exports/portes.nbt | 2026-11-06 | [claude-build] Structure de l'arène Les Portes ; 104×40×104 ; `/place template atelier:portes -52 68 -52` |
| exports/portes_plan.png | 2026-11-06 | [claude-build] Plan de l'arène Les Portes avec la légende des paires, réservé aux organisateurs |
| exports/portes_sans_panneaux.nbt | 2026-11-06 | [claude-build] Correctif : retire les 64 panneaux de l'arène posée ; `/place template atelier:portes_sans_panneaux -39 88 -41` ; inutile avec portes.nbt à jour |
| exports/portes_cibles.json | 2026-11-06 | [claude-build] Points à atteindre pour sources/verif_acces.py |
| arene_portes.md | 2026-11-06 | [claude-build] Liste des paires de portails et des apparitions |
| saves/Arene Porte/datapacks/atelier/ | 2026-11-06 | [claude-build] Datapack du monde Arene Porte : structure portes |
| sources/semaine.py | 2026-11-06 | [claude-build] Monde final : hub à 7 portes, retours, fonctions de pose |
| sources/verif_semaine.py | 2026-11-06 | [claude-build] Contrôle des portes, arrivées, retours et emprises du monde final |
| exports/hub7.nbt | 2026-11-06 | [claude-build] Hub à 7 portes ; 104×34×104 ; posé par `/function atelier:semaine_tout` |
| exports/hub7_plan.png | 2026-11-06 | [claude-build] Plan du hub à 7 portes |
| exports/hub7_cibles.json | 2026-11-06 | [claude-build] Points à atteindre pour sources/verif_acces.py |
| exports/retour_*.nbt | 2026-11-06 | [claude-build] 7 arches de retour vers le hub, une par arène |
| exports/semaine.json | 2026-11-06 | [claude-build] Positions des arènes, portes, arrivées et retours |
| semaine.md | 2026-11-06 | [claude-build] Fiche du monde final Arene Semaine |
| saves/Arene Semaine/datapacks/atelier/ | 2026-11-06 | [claude-build] Datapack du monde final : 15 structures et fonctions semaine_* |
| exports/Arene_Semaine_map.zip | 2026-11-06, refait le 2026-11-08 | [claude-build] Monde final à transmettre : hub et 7 arènes (versions du 2026-11-08, Abysses en 2e retouche avec les modifications de l utilisateur, sans parois invisibles, 10,6 Mo) ; à extraire dans saves ; demande NeoForge 21.1.265 et Create 6.0.10 |
| exports/Arene_Semaine_LISEZMOI.md | 2026-11-06 | [claude-build] Notice et prompt d'installation pour le Claude de l'ami |
| sources/zip_semaine.py | 2026-11-06 | [claude-build] Crée et relit le zip du monde final |
| sources/verif_monde_semaine.py | 2026-11-06 | [claude-build] Compare le monde final aux structures, bloc par bloc, et contrôle les biomes |
| sauvegardes/Arene Piege_2026-11-07_avant_scientifique/ | 2026-11-07 | [claude-build] Copie du monde Arene Piege avant le lieu de vie du scientifique (copiée pendant que le monde était ouvert, juste après une sauvegarde automatique) |
| sauvegardes/piege_avant_scientifique.nbt | 2026-11-07 | [claude-build] Structure de l'arène piège avant le lieu de vie du scientifique ; base du correctif |
| exports/piege_scientifique.nbt | 2026-11-07 | [claude-build] Correctif : lieu de vie du scientifique, roue descendue ; `/place template atelier:piege_scientifique -43 74 -50` après `/kill @e[type=create:stationary_contraption]` |
| sauvegardes/Arene Jungle_2026-11-07_avant_refonte/ | 2026-11-07 | [claude-build] Copie du monde Arene Jungle avant la refonte (monde fermé pendant la copie) |
| sauvegardes/jungle_avant_refonte.nbt | 2026-11-07 | [claude-build] Structure de la jungle avant la refonte ; base du correctif |
| exports/jungle_refonte.nbt | 2026-11-07 | [claude-build] Correctif : jungle refaite (symétrie cassée, sommets accessibles) ; `/place template atelier:jungle_refonte -52 226 -52` |
| exports/jungle_sommets_cibles.json | 2026-11-07 | [claude-build] Contrôle : chaque sommet par sa propre voie, liaisons entre sommets bouchées |
| sauvegardes/Arene Feu_2026-11-07_avant_souterrain/ | 2026-11-07 | [claude-build] Copie du monde Arene Feu avant les travaux du souterrain (monde fermé pendant la copie) |
| sauvegardes/feu2_avant_souterrain.nbt | 2026-11-07 | [claude-build] Structure de l'arène Feu avant les travaux du souterrain |
| sources/scan_souterrain.py | 2026-11-07 | [claude-build] Lecture seule : blocs du monde visibles dans le souterrain d'une arène |
| sauvegardes/feu2_avant_souterrain_decale.nbt | 2026-11-08 | [claude-build] Ancienne arène Feu décalée de 12 en Y, pour comparer avec la nouvelle (base du correctif) |
| exports/feu2_souterrain.nbt | 2026-11-08 | [claude-build] Correctif : parois du souterrain et caverne au lac de lave ; `/place template atelier:feu2_souterrain -40 62 -39` |
| sauvegardes/feu2_souterrain_pose.nbt | 2026-11-08 | [claude-build] Arène Feu telle que posée avec le premier correctif du souterrain ; base du correctif du centre |
| exports/feu2_centre.nbt | 2026-11-08 | [claude-build] Correctif : brasero central à la place de la colonne d'obsidienne, chute de lave rétablie ; `/place template atelier:feu2_centre -39 71 -39` |
| exports/feu2_caverne.png | 2026-11-08 | [claude-build] La caverne du Feu vue de dessus et en coupe |
| exports/feu2_contournement_cibles.json | 2026-11-08 | [claude-build] Contrôle : contourner le lac avec le pont et les îlots bouchés |
| sources/coupe_feu.py | 2026-11-08 | [claude-build] Dessine la caverne du Feu |
| sources/decaler_nbt.py | 2026-11-08 | [claude-build] Décale une structure en hauteur pour comparer deux versions |
| sauvegardes/Nouveau monde_2026-11-08_avant_glace/ | 2026-11-08 | [claude-build] Copie du monde du Croissant avant le labyrinthe glissant (monde fermé pendant la copie) |
| sauvegardes/croissant_avant_glace.nbt | 2026-11-08 | [claude-build] Structure du Croissant avant le labyrinthe glissant ; base du correctif, et de la mise à jour d'Arene Semaine (gabarit -52 80 -308) |
| exports/croissant_glace.nbt | 2026-11-08 | [claude-build] Correctif : sol du labyrinthe en glace compactée, glace bleue et plaques de neige ou de calcite, 8 pièges comblés, congères des culs-de-sac retirées (80×3×93, 1283 blocs). `/reload`, `/tp @s 0 87 0`, `/place template atelier:croissant_glace -46 85 -46` |
| sources/plan_glace.py | 2026-11-08 | [claude-build] Dessine le sol du labyrinthe du Croissant vu de dessus |
| exports/croissant_sol.png | 2026-11-08 | [claude-build] Plan du sol du labyrinthe : glace compactée, glace bleue, plaques non glissantes, poudreuse restante |
| sources/maj_semaine.py | 2026-11-07, refait le 2026-11-08 | [claude-build] Met à jour Arene Semaine avec le Croissant, le Feu, la Jungle et le Piège modifiés depuis sa pose : 4 correctifs et la fonction `semaine_maj` (`/reload` puis `/function atelier:semaine_maj`) |
| exports/semaine_piege.nbt | 2026-11-07 | [claude-build] Correctif de l'arène Piège pour Arene Semaine ; posé par `/function atelier:semaine_maj` |
| exports/semaine_jungle.nbt | 2026-11-07 | [claude-build] Correctif de la Jungle pour Arene Semaine ; posé par `/function atelier:semaine_maj` |
| exports/semaine_croissant.nbt | 2026-11-08 | [claude-build] Correctif du Croissant pour Arene Semaine (labyrinthe glissant) ; posé par `/function atelier:semaine_maj` |
| exports/semaine_feu.nbt | 2026-11-08 | [claude-build] Correctif du Feu pour Arene Semaine (souterrain au lac de lave, brasero) ; posé par `/function atelier:semaine_maj` |
| sauvegardes/Arene Semaine_2026-11-08_avant_maj/ | 2026-11-08 | [claude-build] Copie du monde Arene Semaine avant la mise à jour des 4 arènes |
| exports/sans_barrieres_croissant.nbt, exports/sans_barrieres_feu2.nbt, exports/sans_barrieres_piege.nbt, exports/sans_barrieres_portes.nbt | 2026-11-08 | [claude-build] Structures d'air écrites par sources/semaine.py : retirent les parois invisibles (barrières) des arènes dans Arene Semaine (11 778, 9 724, 916, 924 blocs), posées après chaque arène par semaine_tout et par `/function atelier:semaine_propre` |
| sauvegardes/abysses_avant_retouche.nbt | 2026-11-08 | [claude-build] Structure des Abysses avant la retouche (sortie, symétrie) ; base du correctif d'Arene Semaine |
| sauvegardes/Arene Semaine_2026-11-08_avant_abysses/ | 2026-11-08 | [claude-build] Copie du monde Arene Semaine avant la retouche des Abysses (monde fermé pendant la copie) |
| sources/maj_semaine_abysses.py | 2026-11-08 | [claude-build] Met à jour les Abysses dans Arene Semaine : correctif et fonction de pose ; arguments `<ancienne version posée .nbt> <suffixe>` (1re retouche : `semaine_maj_abysses`, 2e : `semaine_maj_abysses2`) |
| exports/semaine_abysses.nbt | 2026-11-08 | [claude-build] Correctif des Abysses pour Arene Semaine (91×42×99, 20 474 blocs) ; posé par `/function atelier:semaine_maj_abysses` |
| sauvegardes/abysses_retouche1.nbt | 2026-11-08 | [claude-build] Structure des Abysses après la 1re retouche (posée dans Arene Semaine) ; base du correctif de la 2e retouche |
| sauvegardes/Arene Semaine_2026-11-08_avant_abysses2/ | 2026-11-08 | [claude-build] Copie du monde Arene Semaine avant la 2e retouche des Abysses (monde fermé pendant la copie) |
| exports/semaine_abysses2.nbt | 2026-11-08 | [claude-build] Correctif de la 2e retouche des Abysses pour Arene Semaine (104×46×104, 22 288 blocs) ; posé par `/function atelier:semaine_maj_abysses2` |
| exports/abysses_portail.png, exports/abysses_salle.png | 2026-11-08 | [claude-build] Vues rapprochées des Abysses : portail vu du dehors, salle d'entrée en coupe |
| sources/assecher.py | 2026-11-08 | [claude-build] Après un correctif posé sur de l'eau : structures d'air et de blocs secs, car /place garde l'eau du monde dans les marches, bougies, lanternes, chaînes |
| telechargements/ | 2026-11-08 | [claude-build] Téléchargements autorisés par l'utilisateur : JDK 21 portable (Adoptium Temurin 21.0.13) et modèle de mod NeoForge 1.21.1 (NeoForgeMDKs/MDK-1.21.1-ModDevGradle) |
| outils/jdk-21.0.13+9/ | 2026-11-08 | [claude-build] JDK 21 portable décompressé, pour compiler les mods |
| outils/gradle/ | 2026-11-08 | [claude-build] Gradle et NeoForge téléchargés par la compilation (GRADLE_USER_HOME, gardé dans le projet) |
| outils/build_grappin.log | 2026-11-08 | [claude-build] Journal de la dernière compilation du mod Grappin |
| mods_src/grappin/ | 2026-11-08 | [claude-build] Sources du mod Grappin (modèle MDK ; fichiers écrits : gradle.properties, description du neoforge.mods.toml, src/main/java/fr/atelier/grappin/*, src/main/resources/assets/grappin/*) |
| sources/icone_grappin.py, exports/grappin_icone.png | 2026-11-08 | [claude-build] Icône 16×16 du grappin (et sa version agrandie) |
| exports/grappin-1.0.0.jar, launcher/instances/Build-1.21.1/minecraft/mods/grappin-1.0.0.jar | 2026-11-08 | [claude-build] Mod Grappin compilé (NeoForge 21.1.265, Minecraft 1.21.1) : à donner à l'ami, à mettre dans son dossier mods |
| exports/grappin-1.1.0.jar, launcher/instances/Build-1.21.1/minecraft/mods/grappin-1.1.0.jar | 2026-11-08 | [claude-build] Grappin 1.1 : petit bond à l'arrivée pour monter sur les corniches (remplace la 1.0 dans l'instance) |
| sources/donjon_grappin.py | 2026-11-08 | [claude-build] Générateur du donjon n°1 « La Gorge suspendue » (grappin), écrit aussi donjon_grappin.md |
| exports/donjon_grappin.nbt, exports/donjon_grappin_cibles.json | 2026-11-08 | [claude-build] Structure du donjon (100×120×140) et fichier de contrôle |
| sources/verif_grappin.py | 2026-11-08 | [claude-build] Contrôle : impossible sans grappin, possible avec, carte de tous les tirs entre îles |
| sources/plan_donjon.py, exports/donjon_grappin_plan.png | 2026-11-08 | [claude-build] Plan du donjon vu de dessus (réservé aux organisateurs) |
| donjon_grappin.md | 2026-11-08 | [claude-build] Fiche du donjon n°1 : épreuves, coordonnées, contrôle |
| saves/Nouveau monde (1)/datapacks/atelier/ | 2026-11-08 | [claude-build] Datapack du monde de test du donjon n°1 (structure donjon_grappin) |
| sources/zip_donjon.py | 2026-11-08 | [claude-build] Crée le pack pour l'ami : Arene Semaine, donjon n°1, mod Grappin |
| exports/Pack_ami.zip | 2026-11-09 | [claude-build] Pack pour Duncan (refait le 2026-11-09, 15,9 Mo) : saves/Arene Semaine, saves/Donjon 1 (donjon v2 posé), mods/grappin-2.3.0.jar, LISEZMOI (`sources/zip_donjon.py`) |
| exports/grappin-2.0.0.jar (remplacé dans l'instance par la 2.1.0) | 2026-11-09 | [claude-build] Grappin 2.0 : balancier, ne tient que sur les points d'ancrage (grappin:ancrage, tag grappin:ancrages), molette = corde, réglages dans config/grappin-common.toml (remplace la 1.1 dans l'instance) |
| exports/grappin-2.1.0.jar (remplacé dans l'instance par la 2.2.0) | 2026-11-09 | [claude-build] Grappin 2.1 : crochet aimanté aux points d'ancrage frôlés à moins de 1,5 bloc (réglage aimantAncrage) |
| exports/grappin-2.2.0.jar, launcher/instances/Build-1.21.1/minecraft/mods/grappin-2.2.0.jar | 2026-11-09 | [claude-build] Grappin 2.2 : treuil, maintenir la touche de saut rembobine la corde (réglage vitesseTreuil, 0,3 bloc/tick) |
| exports/grappin-2.3.0.jar, launcher/instances/Build-1.21.1/minecraft/mods/grappin-2.3.0.jar | 2026-11-09 | [claude-build] Mod Grappin 2.3.0 : saut au lâcher (`sautAuLacher` 0,42 ajouté à la vitesse verticale). Remplace 2.2.0 dans le dossier mods (2.2.0 reste dans exports) |
| donjon_plan.md | 2026-11-09 | [claude-build] Plan du donjon n°1 refait (phase 2), à valider avant construction |
| sources/schema_donjon2.py, exports/donjon_plan_schema.png | 2026-11-09 | [claude-build] Schéma vue de dessus du plan du donjon refait |
| sources/icone_ancrage.py, exports/ancrage_icone.png | 2026-11-09 | [claude-build] Texture du point d'ancrage (et sa version agrandie) |
| sources/essai_grappin.py, exports/essai_grappin.nbt | 2026-11-09 | [claude-build] Terrain d'essai du balancier (2 tours, 4 ancrages sous une poutre) ; aussi dans le datapack du monde « Donjon 1 » |
| sauvegardes/Donjon 1_2026-11-09_avant_refonte/ | 2026-11-09 | [claude-build] Copie du monde « Donjon 1 » avant la refonte du donjon (monde fermé pendant la copie) |
| sauvegardes/donjon_grappin_v1.nbt | 2026-11-09 | [claude-build] Structure du donjon n°1 avant sa refonte |
| sources/donjon2.py | 2026-11-09 | [claude-build] Générateur du donjon n°1 refait (10 sections, 26 ancrages, 8 salles cachées, 49 commandes) ; écrit la structure, la démolition et les fonctions de pose |
| exports/donjon2.nbt, exports/donjon2_cibles.json | 2026-11-09 | [claude-build] Donjon refait (160×120×176, 307 888 blocs), posé en -75 -61 -85, et son fichier de contrôle. Pose : `/reload` puis `/function atelier:donjon_pose` |
| exports/donjon_vide_v1.nbt | 2026-11-09 | [claude-build] Structure d'air qui démolit l'ancien donjon (posée en -50 -61 -70 par donjon_pose) |
| saves/Nouveau monde (1)/datapacks/atelier/data/atelier/structure/donjon2.nbt, donjon_vide_v1.nbt | 2026-11-09 | [claude-build] Copies des deux structures dans le datapack du monde Donjon 1 |
| saves/Nouveau monde (1)/datapacks/atelier/data/atelier/function/donjon_pose*.mcfunction, donjon_reset.mcfunction | 2026-11-09 | [claude-build] Pose en 3 temps (chargement, démolition + pose, nettoyage) et remise à zéro du donjon |
| sources/verif_donjon2.py | 2026-11-09 | [claude-build] Contrôle par étapes, portes fermées : marche, sauts, grappin (réaliste 14 blocs / généreux 38 blocs), sans grappin |
| sources/fiche_donjon2.py, donjon.md | 2026-11-09 | [claude-build] Fiche du donjon refait : grappin, sections et temps, solutions, ancrages, salles cachées, anciens raccourcis |
| exports/semaine_abysses_air_vide.nbt, exports/semaine_abysses_air_secs.nbt | 2026-11-08 | [claude-build] Assèchement des Abysses dans Arene Semaine (4543 cases d'air, 50 blocs secs) ; posés par `/function atelier:semaine_assecher_abysses` |
| exports/piege_v3.nbt | 2026-11-07 | [claude-build] Correctif : plus de lingots, commandes des dalles mortelles déplacées hors de la fosse, marteaux mortels ; `/place template atelier:piege_v3 -33 82 -7` |
| sources/patch_entites.py | 2026-11-07 | [claude-build] Écrit une structure qui ne repose que les cadres, tableaux et mannequins d'une arène |
| exports/piege_entites.nbt | 2026-11-07 | [claude-build] Cadres, tableau et mannequins de l'arène piège, à reposer seuls après avoir retiré les doublons ; `/place template atelier:piege_entites -31 86 -29` |
| exports/piege_echelle.nbt | 2026-11-07 | [claude-build] Correctif de 2 blocs : poteau plein et échelle du haut de l'observatoire ; `/place template atelier:piege_echelle 22 100 7` |
| sources/verif_pose.py | 2026-11-07 | [claude-build] Compare une structure posée dans un monde avec son .nbt, bloc par bloc, et liste les entités |
| exports/piege_coupe.png | 2026-11-07 | [claude-build] Coupe de l'arène piège au niveau des joueurs, avec la fosse et la roue |
| exports/piege_retour_cibles.json | 2026-11-07 | [claude-build] Contrôle du retour depuis la roue (verif_acces.py) |
| exports/piege_corniche_cibles.json | 2026-11-07 | [claude-build] Contrôle : la corniche doit rester inaccessible depuis la porte |
| sources/verif_scientifique.py | 2026-11-07 | [claude-build] Contrôle : chaque zone de l'arène piège garde deux chemins |
| sources/coupe_piege.py | 2026-11-07 | [claude-build] Dessine la coupe de l'arène piège |
| exports/piege_usine_coupe.png | 2026-11-05 | [claude-build] Coupe de l'arène piège au niveau des joueurs, sans la toiture |
| sources/verif_usine.py | 2026-11-05 | [claude-build] Contrôle de l'usine : chaîne de commandes, tapis, moteurs, presses, roue |
| .venv/ (ajout de Pillow) | 2026-11-04 | Pour dessiner les plans PNG |
| .venv/ | 2026-11-03 | Environnement Python du projet avec nbtlib 2.0.4 (lancer les scripts avec `.venv\Scripts\python.exe`) |

## Builds transmis

| Build | Fichiers | Dimensions (X×Y×Z) | Blocs principaux | Comment le poser |
|---|---|---|---|---|
| Arène Feu (v1) | arene_feu.nbt, arene_feu_plan.png, arene_feu_capture.png | 201×45×201 (cercle Ø200, 129 331 blocs) | briques du Nether, roche noire, netherrack, basalte, sable des âmes, lave, magma | Monde superflat sol à Y188. `/tp @s 0 230 0` puis `/place template atelier:arene_feu -100 178 -100` (centre en X0 Z0, rotation aucune). 10 apparitions à Y189 : (86,0) (70,51) (27,82) (-27,82) (-70,51) (-86,0) (-70,-51) (-27,-82) (27,-82) (70,-51) |
| Map Arene complète | Arene_map.zip (4,1 Mo) | Monde superflat, version 1.21.1, sol Y86 | Forge v2 (centre X0 Z0) + Forge v2 demi (centre X200 Z0) | Décompresser dans le dossier `saves/` de Minecraft, puis ouvrir le monde « Arene » |
| Le Croissant (glace, nouvelle série) | croissant.nbt, croissant_vue.png, arene_glace.md | 104×54×104 (Ø104, 34564 blocs) | glace compacte, glace bleue, neige, calcite, ardoise | `/place template atelier:croissant -52 80 -52` (centre X0 Z0, sol Y86) |
| Arène Feu Ø104 (nouvelle série) | feu2.nbt, feu2_vue.png, arene_feu.md | 104×66×104 (93137 blocs) | netherrack, blackstone, basalte, lave, obsidienne | `/place template atelier:feu2 -52 68 -52` (centre X0 Z0, sol Y86, porte au nord) |
| Arène jungle suspendue (nouvelle série) | jungle.nbt, jungle_vue.png, arene_jungle.md | 104×90×104 (55838 blocs) | bois de jungle, feuilles, mousse, racines | `/place template atelier:jungle -52 226 -52` (Y226 au plus bas, 140 blocs au-dessus du sol Y86) |
| Arène piège (nouvelle série) | piege.nbt, piege_vue.png, arene_piege.md | 104×36×104 (52055 blocs) | pierre, andésite, usine Create en marche | `/place template atelier:piege -52 68 -52` (centre X0 Z0, sol Y86, porte au nord). Demande NeoForge et Create |
| Monde final Arene Hub, version Ø104 | Arene_Hub_Mini_map.zip (12,4 Mo), salles_cachees_hub.md | Monde superflat, version 1.21.1, sol Y86 | Hub et 4 arènes Ø104 reliées | Décompresser dans le dossier `saves/` de Minecraft, puis ouvrir le monde « Arene Hub » |
| Monde final Arene Hub, version grandes arènes | Arene_Hub_map.zip (9,5 Mo) | Monde superflat, version 1.21.1, sol Y86 | Hub et 4 arènes reliées | Décompresser dans le dossier `saves/` de Minecraft, puis ouvrir le monde « Arene Hub » |
| Carrefour des Éléments | hub.nbt, hub_plan.png, hub_vue.png, hub_tout.mcfunction, hub_pose.mcfunction | Hub 228×56×228 (Ø104 et 4 avenues) + 4 arènes | quartz, pierre, thèmes des arènes | Monde Arene Hub : `/function atelier:hub_tout`. Glace au nord (centre Z-240), Air à l'est (X210), Feu au sud (Z240), Nature à l'ouest (X-240) |
| Map Arene Glace complète | Arene_Glace_map.zip (3,4 Mo) | Monde superflat, version 1.21.1, sol Y86 | Arène Glace (centre X0 Z0) | Décompresser dans le dossier `saves/` de Minecraft, puis ouvrir le monde « Arene Glace » |
| Arène Glace | arene_glace.nbt, arene_glace_plan.png, arene_glace_vue.png, salles_cachees_glace.md | 252×84×252 (cercle Ø252, 325636 blocs) | glace compacte, glace bleue, neige, calcite, diorite polie, ardoise des abîmes, prismarine | Le coin se place 126 blocs à l'ouest/nord du centre et 18 blocs sous le sol : `/place template <ns>:arene_glace <cx-126> <sol-18> <cz-126>`. Monde Arene Glace : `/place template atelier:arene_glace -126 68 -126`. 8 salles, 4 pièges, 10 apparitions : voir salles_cachees_glace.md |
| Map Arene Nature complète | Arene_Nature_map.zip (5,0 Mo) | Monde superflat, version 1.21.1, sol Y86 | Arène Nature (centre X0 Z0) | Décompresser dans le dossier `saves/` de Minecraft, puis ouvrir le monde « Arene Nature » |
| Arène Nature | arene_nature.nbt, arene_nature_plan.png, arene_nature_vue.png, salles_cachees_nature.md | 252×84×252 (cercle Ø252  426 300 blocs) | pierre moussue, bois, feuilles, mousse, boue, eau | Le coin se place 126 blocs à l'ouest/nord du centre et 18 blocs sous le sol : `/place template <ns>:arene_nature <cx-126> <sol-18> <cz-126>`. Monde Arene Nature : `/place template atelier:arene_nature -126 68 -126`. 8 salles, 10 apparitions : voir salles_cachees_nature.md |
| Map Arene Air complète | Arene_Air_map.zip (3,9 Mo) | Monde superflat, version 1.21.1, zone basse Y86, plateau Y104 | Arène Air (centre X0 Z0) | Décompresser dans le dossier `saves/` de Minecraft, puis ouvrir le monde « Arene Air » |
| Arène Air | arene_air.nbt, arene_air_plan.png, arene_air_vue.png, salles_cachees_air.md | 192×76×192 (cercle Ø192, 224 164 blocs) | quartz, calcite, béton blanc, laine, verre bleu/cyan, cuivre oxydé ciré, purpur | Le coin se place 96 blocs à l'ouest/nord du centre et 12 blocs sous le sol bas : `/place template <ns>:arene_air <cx-96> <sol-12> <cz-96>`. Monde Arene Air : `/place template atelier:arene_air -96 74 -96`. 8 salles, 14 remontées, 10 apparitions : voir salles_cachees_air.md |
| Temple des Quatre Vents (remplacé) | quatre_vents.nbt, quatre_vents_plan.png, salles_cachees_air.md | 252×76×252 (cercle Ø252, 187 205 blocs) | quartz, glace, cerisier, grès rouge, ardoise des abîmes, cuivre oxydé | `/place template <ns>:quatre_vents <cx-126> <sol-12> <cz-126>`. Monde Arene Air : `/place template atelier:quatre_vents -126 74 -126`. 8 salles cachées et 10 apparitions : voir salles_cachees_air.md |
| Forge v2 demi | forge_v2_demi.nbt, forge_v2_demi_plan.png, salles_cachees_demi.md | 126×44×126 (cercle Ø126, 67 740 blocs) | idem Forge v2 | `/place template <ns>:forge_v2_demi <cx-63> <sol-12> <cz-63>`. Monde Arene : `/place template atelier:forge_v2_demi 137 74 -63` (centre X200 Z0). 9 salles cachées : voir salles_cachees_demi.md |
| Forge v2 | forge_v2.nbt, forge_v2_plan.png, salles_cachees.md | 252×56×252 (cercle Ø252, 247 585 blocs) | pierre noire polie, briques du Nether, basalte, obsidienne, sable/terre des âmes, lave, magma | Le coin du gabarit se place 126 blocs à l'ouest/nord du centre voulu et 12 blocs sous le sol : `/place template <ns>:forge_v2 <cx-126> <sol-12> <cz-126>`. Monde Arene : `/place template atelier:forge_v2 -126 74 -126`. 9 salles cachées : voir salles_cachees.md |
| Arène Forge (v1) | arene_forge.nbt, arene_forge_plan.png | 127×36×127 (cercle Ø126, circonf. 395,8 ; 139 722 blocs) | roche noire polie, basalte, magma, lave, netherrack, briques du Nether rouges | Cuvette en gradins (Y188 → Y180) + lac de lave. `/tp @s 200 230 0` puis `/place template atelier:arene_forge 137 172 -63` (centre X200 Z0). 10 apparitions à Y189 : (253,17) (233,45) (200,55) (167,45) (147,17) (147,-17) (167,-45) (200,-55) (233,-45) (253,-17) |
| (déplacé) D:\PROJET\minecraft-2026-2027 | 2026-11-09 | Clone du dépôt GitHub waeter-alt/minecraft-2026-2027 (projet Kodistan / mod Interrègne de Duncan), déplacé hors de l'Atelier à la demande de l'utilisateur (exception à la règle 1 pour ce seul dossier) pour que seul son CLAUDE.md s'y applique. Pas un fichier [claude-build]
| .venv : paquet pypdf | 2026-11-09 | [claude-build] Installé dans le Python du projet pour lire les PDF (guide du projet Kodistan) |
| outils/gh/ | 2026-11-09 | [claude-build] GitHub CLI 2.103.3 portable (zip officiel github.com/cli/cli, somme sha256 contrôlée), pour `gh auth login` et les Pull Requests du dépôt Kodistan |
