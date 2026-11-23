# Boss « Séraphin » — conception

Date : 2026-11-12 · Auteur : Luca (avec Claude) · Cible : mod Grappin (`atelier/donjon/arene`), NeoForge figé du projet (21.1.216, Minecraft 1.21.1)

## Objectif

Un boss pour l'arène du donjon n°3 « L'Archipel des Nuées » : un mob volant, le **Séraphin**, avec un œuf de spawn. Il s'inspire d'une gravure de séraphin : un grand œil central sans corps visible, entouré de plusieurs paires d'ailes (à plumes et de chauve-souris). La ressemblance n'a pas à être exacte, seulement l'esprit (l'œil et les ailes).

**Périmètre de cette étape : le mob seul.** On le pose à la main avec l'œuf de spawn pour le tester. Le branchement au donjon (apparition dans l'arène quand les trois éclats sont posés, ouverture de la sortie et du coffre à sa mort) est une étape suivante, hors de ce document.

## Contexte

- L'arène existe déjà dans le générateur de l'archipel (`donjon_ciel*.py`) : île « BO », avec `boss_pos`, `boss_coffre`, `boss_sortie` ; la sortie, une grille de barreaux, s'ouvre quand les trois éclats sont sur l'autel, et une fonction `ciel_ouvrir_sortie_boss` est prévue pour la mort du boss.
- Les joueurs n'ont que la Paravoile. Tomber dans le vide ramène au dernier point de passage (donc un recul violent n'est pas mortel).
- Le mod Grappin enregistre déjà une entité (le crochet), un rendu client et un fichier de réglages (`Reglages.java`). Le boss s'y ajoute : un seul jar à donner à Duncan, déjà pris en compte par le raccourci de test.

## Le mob

- Entité `grappin:seraphin`, catégorie monstre, qui vole sans toucher le sol et garde une altitude fixe au-dessus de sa cible ; il tourne lentement autour d'elle.
- Boîte de collision d'environ 3 × 3 blocs.
- Barre de vie de boss (`ServerBossEvent`), visible par les joueurs proches.
- Œuf de spawn dans l'onglet créatif, nom français « Séraphin ».
- Vie, dégâts et durées de ses attaques réglables dans `config/grappin-common.toml` (ajoutés à `Reglages.java`), sans recompiler.
- Il ne despawn pas (persistant) et ne brûle pas.

## Le modèle

- Œil central sphérique ; la pupille suit le joueur ciblé.
- Trois paires d'ailes à plumes (deux paires dressées vers le haut, une paire grande qui retombe) et une paire d'ailes de chauve-souris sur les côtés.
- Les ailes battent lentement en vol, plus vite pendant une attaque ; l'œil brille pendant la charge du rayon.
- Modèle écrit directement dans le code (couches de modèle vanilla, sans bibliothèque supplémentaire) ; texture générée en noir et blanc, gravure, à la manière de l'image de référence (Pillow).

## Les attaques

1. **Rayon de l'œil** — l'œil s'ouvre et brille 1,5 s (avertissement), puis un rayon droit part vers le joueur. On l'esquive en planant sur le côté.
2. **Volée de plumes** — un éventail de petits projectiles (entité `grappin:plume`, légère, sans gravité forte) aux dégâts modérés.
3. **Balayage d'ailes** — si un joueur est trop proche, les ailes le repoussent fortement (non mortel : le vide ramène au point de passage).
4. **Phase 2** — sous la moitié de sa vie, il enchaîne plus vite et tire deux rayons.

## Points faibles

Ailes fermées, il prend des dégâts réduits ; pendant que l'œil est ouvert (charge puis tir du rayon), il prend les dégâts normaux. Le facteur de réduction est un réglage.

## Découpage du code (fichiers du mod Grappin)

- `Seraphin.java` — l'entité : attributs, barre de vie, IA des attaques (objectifs/`Goal`).
- `Plume.java` — le projectile.
- `SeraphinModele.java`, `SeraphinRendu.java` (côté client) — modèle, animation, rendu.
- `Grappin.java` — enregistrement de l'entité, du projectile et de l'œuf ; `GrappinClient.java` — enregistrement du rendu ; `Reglages.java` — réglages.
- Ressources : texture de l'entité, entrées de langue (`fr_fr`, `en_us`).

## Tests

- Compilation du mod (`./gradlew build` dans `atelier/donjon/arene`, JDK 21 du dépôt).
- Test en jeu par Luca avec le client de test : œuf de spawn, apparition, barre de vie, chacune des trois attaques, réduction des dégâts ailes fermées, phase 2, mort (sans erreur dans les logs).
- Nouveau jar versionné (2.7.0) copié dans `atelier/exports/` (le raccourci de test prend le plus récent).

## Hors périmètre

Apparition automatique dans l'arène, ouverture de la sortie et du coffre à la mort, musique, butin particulier. À faire dans une étape suivante, avec régénération des zips de monde du donjon.

## Points à surveiller

- Zone « mixins » : aucune prévue ; le boss n'utilise que l'API NeoForge standard.
- La version de NeoForge (`neo_version`) n'est pas touchée.
- L'équilibrage (vie, dégâts) est volontairement dans la configuration : il se règle au test, pas dans le code.
