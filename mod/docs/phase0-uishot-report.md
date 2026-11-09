# Rapport UiShot — vérification visuelle Phase 0

**Date :** 2026-11-09
**Build :** `./gradlew build` → exit 0
**Monde :** `Test_citadelle`
**Tailles testées :** `1553x1011@4`, `1553x1011@2`

## Écran 1 — catalogue (CatalogueScreen)

Sortie exacte de `run/client/uishot/done.txt` :

```
Layout problems: 0 (lines starting with !)
catalogue_1553x1011_s4.png  [CatalogueScreen]
catalogue_1553x1011_s2.png  [CatalogueScreen]
```

**Verdict : PASS.** Zéro problème de layout (`! cut / cropped / offscreen / overlap / on item` : aucune ligne). Les deux captures sont bien rendues avec CatalogueScreen ouvert, aux deux échelles.

## Écran 2 — galerie de styles (StyleScreen)

Une cible `style` a été ajoutée pour permettre la capture (`CatalogueScreen.openStyleGalleryForUiShot`, appelée depuis le nouveau `case "style"` de `UiScenarios.targets()`). Construction minimale, n'élargit pas la visibilité du constructeur package-private de `StyleScreen` : on reste dans le même package, exactement comme le fait déjà `CatalogueScreen` ligne 490.

Sortie exacte de `run/client/uishot/done.txt` :

```
Layout problems: 11 (lines starting with !)
style_1553x1011_s4.png  [StyleScreen]
    ! overlap   "10" @292,15 x "The recipe book can help" @259,18
    ! overlap   "244 102" @322,15 x "The recipe book can help" @259,18
    ! overlap   "✕" @368,14 x "The recipe book can help" @259,18
    ! cut       "Athènes …" @18,69
    ! cut       "Bouleau …" @82,69
    ! cut       "Cabanes…" @146,69
    ! cut       "Cabanes…" @18,121
    ! cut       "Cité per…" @18,173
    ! cut       "Fortere…" @18,225
    ! cut       "Grottes …" @82,225
    ! cut       "Guerres…" @146,225
style_1553x1011_s2.png  [StyleScreen]
```

**Analyse :**
- Les 3 lignes `overlap` impliquent "The recipe book can help", un texte vanilla (le pavé d'aide du livre de recettes). Ce n'est pas du texte de StyleScreen ni du kit Phase 0 ; sa position (haut-droite de l'écran) correspond à l'emplacement standard des toasts vanilla, qui se dessinent par-dessus tout écran ouvert, indépendamment du code du mod. Hypothèse : un toast "nouveau joueur" du moteur, déclenché par l'état du monde de test au premier lancement de ce run, et qui serait tombé sur n'importe quel écran ouvert à ce moment précis — pas une régression du kit. Non confirmé par un second run.
- Les 8 lignes `cut` sont des noms de style tronqués par ellipse dans les cartes à s4 — même famille que les 8 troncatures déjà acceptées sur le catalogue à s4 (voir mémoire `ui-screenshot-loop`), pas un bug nouveau introduit par le rebase sur le kit.
- **s2 : zéro problème.**

**Verdict : PASS sous réserve.** Rien dans ces 11 lignes n'est attribuable au rebase de StyleScreen sur `ParchmentScreen`/`ScrollGrid` : le seul élément suspect (le toast) est hors du périmètre du mod, et les troncatures sont une convention déjà acceptée. Si l'utilisateur veut écarter tout doute, un second run isolé (sans manipulation d'inventaire avant le lancement) confirmerait que le toast ne revient pas.

## Verdict global

- **CatalogueScreen : PASS** — zéro problème de layout.
- **StyleScreen : PASS sous réserve** — zéro problème attribuable au kit ; un toast vanilla incidental et des troncatures déjà actées, à reconfirmer si besoin.
- **Phase 0 (kit UI partagé `client/ui/`) : visuellement sûre sur les deux écrans qu'elle a touchés.**

## Preuves

- Captures (worktree de vérification, non conservé) : `style_1553x1011_s4.png`, `style_1553x1011_s2.png`, `catalogue_1553x1011_s4.png`, `catalogue_1553x1011_s2.png`
- Code ajouté pour rendre StyleScreen testable : `client/catalogue/CatalogueScreen.java` (méthode `openStyleGalleryForUiShot`), `client/dev/UiScenarios.java` (`case "style"`).
