# Crédits des sons des troupes (légionnaire, archère)

Les voix des deux troupes viennent de banques libres téléchargées sur OpenGameArt (2026-11-18), découpées en
sons séparés, passées en mono et converties en Ogg Vorbis (script jetable, pas dans le dépôt). Elles remplacent les
répliques génériques de pilleur de MineColonies (« who wants to fight » et les cris de douleur / de mort).
Les fichiers utilisés sont sous `mod/src/main/resources/assets/interregne/sounds/troops/`, listés dans `sounds.json`.

| Usage | Source | Auteur | Licence |
|---|---|---|---|
| Légionnaire : blessure (`hurt1-5`), cris d'attaque (`attack1-5`) | [Male Grunt/Yelling sounds](https://opengameart.org/content/male-gruntyelling-sounds) | megaboots | CC0 (double licence OGA-BY 3.0 / CC0, on prend CC0) |
| Légionnaire : mort (`death1-5`) | [grunts of male death and pain](https://opengameart.org/content/grunts-male-death-and-pain) | Pompei2 | CC0 |
| Archère : mort (`death1-4`) | [Female screams](https://opengameart.org/content/female-screams) : les 4 fichiers CC0 de l'archive (tcrocker68 x2, pushkin, Archeos, via freesound.org) | OmeroIvey (compilation) | CC0 |
| Archère : blessure (`hurt1-6`), effort de tir (`shoot1-5`) | [Female Gruntwork - 1](https://opengameart.org/content/female-gruntwork-1) | Snabisch | **CC BY-SA 4.0** : attribution obligatoire, et toute adaptation de ces sons reste en CC BY-SA 4.0 |

Seules les voix d'archère `hurt` et `shoot` ne sont pas CC0. Si la licence pose problème, les remplacer par des
sons CC0 (ou retirer `archer.hurt` / `archer.shoot` de `sounds.json`).
