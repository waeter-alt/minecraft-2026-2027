# Mods hors Modrinth et écarts : bilan de la résolution

Source : les 54 jars de `KODISTAN - L' Artefact du Chaos/mods/` (serveur 2025), plus le shaderpack de `shaderpacks/`.
Chaque jar a été haché en SHA-1 puis cherché via `POST https://api.modrinth.com/v2/version_files`. Les jars introuvables ont ensuite été cherchés par slug et numéro de version.

| Résultat | Nombre | Détail |
|---|---|---|
| Modrinth, fichier identique au bit près | 44 | même SHA-1 que le jar 2025 |
| Modrinth, même version, build différent | 4 | voir § 2 |
| CurseForge uniquement (LDTTeam) | 6 | voir § 1, entrées `mode = "metadata:curseforge"` |
| Introuvable | **0** | |
| Shaderpack Complementary Unbound r5.6.1 | 1 | Modrinth, identique, `side = "client"` |
| Notre mod `interregne` | 1 | auto-hébergé, voir le README |

Toutes les versions 2025 sont conservées à l'identique, MineColonies compris, comme décidé. Chaque entrée est marquée `pin = true`, donc `packwiz update --all` n'y touche pas.

## 1. Mods CurseForge uniquement (LDTTeam)

MineColonies et ses dépendances ne sont pas publiés sur Modrinth : le projet `minecolonies` y existe, mais il est figé en 1.18.2. Les IDs de projet et de fichier viennent de `minecraftinstance.json` (l'instance CurseForge de 2025). Les hashes SHA-1 sont ceux des jars téléchargés depuis CurseForge.

| Mod | Fichier | projet / fichier CF | Licence (dépôt GitHub) |
|---|---|---|---|
| MineColonies | minecolonies-1.1.1041-1.21.1.jar | 245506 / 6790456 | GPL-3.0 |
| Structurize | structurize-1.0.793-1.21.1.jar | 298744 / 7214034 | GPL-3.0 |
| BlockUI | blockui-1.0.205-1.21.1.jar | 522992 / 6646615 | GPL-3.0 |
| Domum Ornamentum | domum-ornamentum-1.0.223-snapshot-main.jar | 527361 / 7231908 | GPL-3.0 |
| Multi-Piston | multipiston-1.2.58-1.21.1.jar | 303278 / 7097877 | à vérifier (dépôt `ldtteam/multi-piston` introuvable) |
| TownTalk | towntalk-1.2.0.jar | 900364 / 5653504 | non déclarée (`NOASSERTION`) |

**Risque :** packwiz-installer télécharge ces fichiers via l'API CurseForge. Si l'auteur a désactivé la « distribution tierce » d'un fichier, l'installeur affiche une fenêtre « téléchargement manuel requis », ce qui casse le zéro-config.
Le drapeau n'est pas lisible sans clé d'API CurseForge, donc **il n'a pas été vérifié**. Ces mods figurent dans de nombreux modpacks tiers, ce qui laisse penser que la distribution est autorisée, mais rien n'est garanti.

**Action :** au premier lancement de test (README, étape 5), vérifier que ces 6 mods s'installent sans fenêtre manuelle. En cas de blocage :

- **MineColonies, Structurize, BlockUI, Domum Ornamentum** (GPL-3.0) : on peut les redistribuer nous-mêmes, à condition de pointer vers le code source (github.com/ldtteam). Avec le jar 2025 sous la main :
  ```powershell
  .\launcher\scripts\publish-jar.ps1 -PackUrl <URL de pack.toml> -Slug minecolonies -Name MineColonies `
      -Jar "C:\...\KODISTAN - L' Artefact du Chaos\mods\minecolonies-1.1.1041-1.21.1.jar"
  ```
  Le script remplace `mods/minecolonies.pw.toml` par une entrée « URL directe » vers `files/` sur notre hébergement, puis rafraîchit l'index.
- **Multi-Piston :** même procédure, après avoir vérifié sa licence (fichier LICENSE dans le jar ou page CurseForge).
- **TownTalk :** voix des citoyens MineColonies, 52 Mo, purement cosmétique. La licence n'est pas claire, donc on ne l'auto-héberge pas. Si elle bloque, on la **retire** : `packwiz remove towntalk`, puis on enlève `"mod/towntalk:respack"` de `prism-instance/minecraft/options.txt`. Aucun remplacement n'est nécessaire.

## 2. Même version sur Modrinth, mais pas le même fichier

Ces jars 2025 venaient de CurseForge. Modrinth publie **la même version**, avec un build légèrement différent : quelques octets d'écart, probablement l'horodatage ou la signature. On a pris le fichier Modrinth de la même version. Aucun impact fonctionnel n'est attendu, mais le **serveur** doit utiliser ces mêmes fichiers. C'est automatique si on l'installe avec packwiz (voir HOSTING.md).

| Mod | Version | SHA-1 du jar 2025 (CF) | SHA-1 Modrinth |
|---|---|---|---|
| Forgified Fabric API | 0.115.6+2.1.4+1.21.1 | 3b77e979… | f9833554… |
| Sophisticated Backpacks | 1.21.1-3.25.16.1434 | 1499960b… | 877bd9c4… |
| Sophisticated Core | 1.21.1-1.3.91.1272 | a6533bcc… | e59facb8… |
| Simple Voice Chat | neoforge-1.21.1-2.6.7 | 4250c1a9… | 2358b163… |

## 3. Ignoré volontairement

- `mods/update/DistantHorizons-2.4.4-b-1.21.1.jar` : une ancienne copie rangée dans un sous-dossier. NeoForge ne charge pas les sous-dossiers ; c'est la 2.4.5 qui tournait.
- **Configs 2025** (`config/`) : elles ne sont pas poussées par le pack, parce que packwiz réécrirait les réglages des joueurs à chaque mise à jour. Seules les configs serveur sont à reprendre ; voir HOSTING.md. Pour imposer une config client, voir le README (« Pousser une config »).
- **Datapack 2025** : il appartient au monde du serveur, pas au pack client.

## 4. Côtés (`side`) choisis

- La valeur par défaut vient des champs `client_side` / `server_side` de Modrinth, puis elle est corrigée par les dépendances réelles.
- **Client uniquement :** Sodium, Iris, Team Capes, Enchantment Descriptions et le shaderpack.
- **Serveur uniquement :** Gazebos et Tree Harvester (côté client « unsupported » sur Modrinth).
- **Tout le reste est `both`.** Structure Pool API est marqué « serveur » sur Modrinth, mais Wizards, Paladins, Rogues, Archers et Jewelry l'exigent aussi côté client. Xaero's, AppleSkin, JEI, Lithium et Simple Voice Chat ont une partie serveur utile.
