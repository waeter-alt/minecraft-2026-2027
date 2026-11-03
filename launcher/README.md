# Lanceur de L'Interrègne : guide de l'organisateur

Objectif : un ami reçoit un zip, le décompresse, double-clique, se connecte une fois avec son compte Microsoft et arrive **directement sur le serveur**, en français, avec tous les mods à jour. Aucune configuration de son côté.

```
 GitHub Pages (pack statique)            Zip "Interregne" chez chaque joueur
 ┌──────────────────────────┐            ┌─────────────────────────────────────────────┐
 │ pack.toml / index.toml   │◄───────────┤ Prism Launcher portable                     │
 │ mods/*.pw.toml           │  à chaque  │  instances/Interregne/                       │
 │ files/interregne-x.jar   │  lancement │   instance.cfg  (pré-lancement + auto-join)  │
 └──────────────────────────┘            │   minecraft/packwiz-installer-bootstrap.jar  │
   Les .pw.toml pointent vers            │   minecraft/options.txt (fr_fr)              │
   Modrinth et CurseForge                │   minecraft/servers.dat                      │
                                         └─────────────────────────────────────────────┘
```

Contenu de ce dossier :

| Chemin | Rôle |
|---|---|
| `pack/` | Le modpack au format packwiz (MC 1.21.1, NeoForge 21.1.216, 55 mods et 1 shaderpack). **C'est ce dossier qu'on publie.** |
| `prism-instance/` | Le modèle d'instance Prism : `instance.cfg`, `mmc-pack.json`, `minecraft/options.txt`. |
| `scripts/` | `prepare-instance.ps1`, `make-zip.ps1`, `publish-jar.ps1` et `refresh-index.ps1`. |
| `MISSING_MODS.md` | Ce qui n'est pas sur Modrinth (MineColonies & co.) et les actions à prévoir. |
| `HOSTING.md` | Où et comment héberger le serveur. |

## Les marqueurs à remplacer

| Marqueur | Où | Valeur attendue |
|---|---|---|
| `PACK_URL` | `prism-instance/instance.cfg` | URL complète de `pack.toml`, par ex. `https://moncompte.github.io/interregne-pack/pack.toml` |
| `SERVER_ADDRESS` | `prism-instance/instance.cfg` | Adresse du serveur, par ex. `play.exemple.fr` ou `1.2.3.4:25565` |
| `PACK_BASE_URL` et `A_REMPLIR_PAR_scripts-publish-jar.ps1` | `pack/mods/interregne.pw.toml` | Remplis automatiquement par `scripts/publish-jar.ps1` |

Ne les modifie pas à la main dans les modèles. `prepare-instance.ps1` les remplace dans la **copie** installée dans Prism, et `publish-jar.ps1` remplit l'entrée `interregne`.

---

## Étape 1 : télécharger les outils (une seule fois)

Ce dépôt ne contient aucun exécutable : récupère-les toi-même depuis leurs sources officielles.

1. **Prism Launcher, version portable pour Windows** : sur prismlauncher.org, Download → Windows → l'archive « Portable » (zip). Dézippe-la dans un **chemin court**, par ex. `C:\Interregne\PrismLauncher\`. Les chemins longs posent problème à Windows PowerShell 5.1.
2. **packwiz-installer-bootstrap.jar** : page Releases de `github.com/packwiz/packwiz-installer-bootstrap`. Garde le `.jar` dans `Téléchargements`.
3. **packwiz** (la ligne de commande, recommandée pour maintenir le pack) : il n'y a pas de release officielle. Au choix :
   - `go install github.com/packwiz/packwiz@latest` (Go 1.21 ou plus) ;
   - l'artefact Windows du dernier build « Actions » sur `github.com/packwiz/packwiz`.

   Mets `packwiz.exe` dans le PATH. Sans packwiz, `scripts/refresh-index.ps1` sert de roue de secours pour recalculer les hashes.
4. **Java 21** est déjà sur ton PC pour compiler le mod. Les joueurs n'ont rien à installer : Prism télécharge Java tout seul (voir l'étape 4).

## Étape 2 : héberger le pack sur GitHub Pages

Le dépôt principal contient le code du mod et le dossier de projet : il est privé, et Pages n'est gratuit que pour les dépôts publics. On publie donc **uniquement `launcher/pack/`** dans un petit dépôt public dédié.

1. Sur GitHub, crée un dépôt **public** vide, par ex. `interregne-pack`.
2. Depuis la racine de ce dépôt-ci :
   ```powershell
   git remote add pages https://github.com/<compte>/interregne-pack.git
   git subtree push --prefix launcher/pack pages main
   ```
3. Dans `interregne-pack` sur GitHub : Settings → Pages → *Deploy from a branch* → `main` / `(root)`.
4. Au bout d'une minute, `https://<compte>.github.io/interregne-pack/pack.toml` doit s'afficher dans le navigateur. **C'est ton `PACK_URL`.**

Notes :

- Le fichier `.nojekyll` est déjà présent : Pages sert les fichiers tels quels.
- `pack/.gitattributes` force les fins de ligne LF. packwiz hache les fichiers octet par octet : un CRLF ajouté par Git sous Windows casserait tous les hashes.
- Le cache de GitHub Pages dure environ 10 minutes. Après une mise à jour, les joueurs qui lancent tout de suite peuvent encore recevoir l'ancienne version.
- Alternatives à GitHub Pages : Cloudflare Pages, Netlify, ou n'importe quel hébergement de fichiers statiques. Il suffit que `pack.toml` soit accessible en HTTPS.

## Étape 3 : publier notre mod `interregne`

```powershell
cd mod
.\gradlew build                       # produit mod\build\libs\interregne-1.0.0.jar
cd ..
.\launcher\scripts\publish-jar.ps1 -PackUrl https://<compte>.github.io/interregne-pack/pack.toml
git add launcher/pack ; git commit -m "Pack : interregne 1.0.0"
git subtree push --prefix launcher/pack pages main
```

Le script :

1. copie le jar dans `pack/files/`, qui est servi par Pages mais exclu de l'index ;
2. écrit l'URL et le hash SHA-512 dans `pack/mods/interregne.pw.toml` ;
3. lance `packwiz refresh`, ou `refresh-index.ps1` si packwiz n'est pas installé.

**Tant que cette étape n'est pas faite, l'installation du pack échoue sur `interregne`.** C'est voulu : le hash est encore un marqueur.

## Étape 4 : construire le dossier Prism portable

1. Lance `C:\Interregne\PrismLauncher\prismlauncher.exe` **une fois** et suis l'assistant de démarrage :
   - langue **Français** ;
   - Java : choisis le **téléchargement automatique de Java**, pour que les joueurs n'aient rien à installer ;
   - thème : au choix.

   Ces réglages sont enregistrés dans `prismlauncher.cfg`, qui part dans le zip.
2. Ferme Prism, puis installe l'instance :
   ```powershell
   .\launcher\scripts\prepare-instance.ps1 -PrismDir C:\Interregne\PrismLauncher `
       -PackUrl https://<compte>.github.io/interregne-pack/pack.toml `
       -ServerAddress play.exemple.fr `
       -BootstrapJar $HOME\Downloads\packwiz-installer-bootstrap.jar
   ```
   Le script crée `instances\Interregne\` à partir de `prism-instance/` et remplace `PACK_URL` et `SERVER_ADDRESS`. Il y ajoute le bootstrap, un `servers.dat` (le serveur apparaît dans « Multijoueur » et ses packs de ressources sont acceptés d'office) et un `Jouer.bat` à la racine de Prism.
3. Rouvre Prism. Clic droit sur « L'Interrègne » → *Modifier* → *Paramètres* : vérifie en un coup d'œil les points suivants.
   - **Commandes personnalisées**, pré-lancement : `"$INST_JAVA" -jar packwiz-installer-bootstrap.jar https://…/pack.toml`. Si Prism affiche des `\"`, retape la commande à la main sous cette forme.
   - **Java** : mémoire de 2 Go à 6 Go, plus les arguments JVM.
   - **Serveur** : « Rejoindre un serveur au lancement » est coché, avec la bonne adresse.

Réglages de l'instance (`instance.cfg`) :

- **Mémoire de 2 à 6 Go.** Avec Distant Horizons et environ 55 mods, 6 Go est confortable. Pour un ami qui n'a que 8 Go de RAM, descendre à 4 Go dans les paramètres de l'instance.
- **JVM :** G1 réglé pour des pauses courtes (`MaxGCPauseMillis=100`, régions de 32 Mo) et `-Dfile.encoding=UTF-8` pour les accents.
- **Auto-join :** `JoinServerOnLaunch=true`. Prism lance Minecraft avec `--quickPlayMultiplayer`.
- **`options.txt` :** `lang:fr_fr`, distance de rendu 12 et de simulation 8 (Distant Horizons s'occupe du lointain), pas d'auto-jump, écran d'accessibilité et tutoriel sautés, voix de TownTalk activées.
- **Touches :** aucune n'est imposée. Minecraft retient la position physique des touches, donc un clavier AZERTY obtient ZQSD sans rien régler.

## Étape 5 : tester

1. Dans Prism, ajoute **ton** compte Microsoft (en haut à droite → *Gérer les comptes* → *Ajouter Microsoft*).
2. Lance l'instance. La fenêtre packwiz télécharge environ 55 mods au premier lancement, puis Minecraft démarre et se connecte au serveur.
3. Vérifie :
   - les 6 mods CurseForge (MineColonies & co.) s'installent **sans** fenêtre « téléchargement manuel » (voir `MISSING_MODS.md` sinon) ;
   - le jeu est en français ;
   - l'auto-join fonctionne ;
   - Simple Voice Chat se connecte.

## Étape 6 : fabriquer le zip et le distribuer

```powershell
.\launcher\scripts\make-zip.ps1 -PrismDir C:\Interregne\PrismLauncher
# -> launcher\dist\Interregne-AAAA-MM-JJ.zip
```

Le script **exclut `accounts.json`**, qui contient tes jetons Microsoft ; il s'arrête net s'il en trouve un.

- En mode léger (par défaut), il exclut aussi les logs, les caches, Java, les assets et les mods déjà téléchargés. Le zip pèse quelques dizaines de Mo, et tout se télécharge proprement au premier lancement chez chaque joueur.
- `-Full` garde tout : zip de plusieurs centaines de Mo, mais premier lancement plus rapide.

Partage le zip par Google Drive, WeTransfer, Discord Nitro, etc. **Il n'est à refaire que si l'instance elle-même change** : adresse du serveur, URL du pack ou mémoire. Les mods se mettent à jour tout seuls.

## Étape 7 : mettre à jour le pack pendant la préparation ou l'événement

Les mises à jour se font toutes dans `launcher/pack/`. Pour les publier :

```powershell
git add launcher/pack ; git commit -m "Pack : …"
git subtree push --prefix launcher/pack pages main
```

Les joueurs reçoivent la mise à jour **à leur prochain lancement**.

| Je veux… | Commande (dans `launcher/pack/`) |
|---|---|
| Publier une nouvelle version de notre mod | Monter `mod_version` dans `mod/gradle.properties`, lancer `.\gradlew build`, puis `..\scripts\publish-jar.ps1 -PackUrl …`. L'ancien jar est retiré de `files/` et packwiz-installer supprime l'ancien fichier chez les joueurs. |
| Ajouter un mod Modrinth | `packwiz modrinth add <slug>`, puis vérifier `side` dans le `.pw.toml` |
| Retirer un mod | `packwiz remove <slug>` |
| Mettre à jour un mod précis | `packwiz unpin <slug>`, `packwiz update <slug>`, puis `packwiz pin <slug>` |
| Recalculer l'index après une modif manuelle | `packwiz refresh` (ou `..\scripts\refresh-index.ps1`) |
| Pousser une config | Copier le fichier sous `pack/config/...`, puis `packwiz refresh`. Attention : il écrase la version du joueur à chaque changement de hash. |
| Ajouter FancyMenu / Drippy Loading Screen (habillage) | `packwiz modrinth add fancymenu` et `packwiz modrinth add drippy-loading-screen` (avec leur dépendance Konkrete si demandée), plus leurs fichiers dans `pack/config/fancymenu/` |

Bonnes pratiques :

- Tous les mods 2025 sont **épinglés** (`pin = true`), MineColonies compris : `packwiz update --all` ne les touche pas. On garde les versions 2025, comme décidé.
- Monte `version` dans `pack.toml` à chaque publication (1.0.0, 1.0.1…). C'est purement indicatif, mais pratique pour s'y retrouver.
- Le serveur doit avoir **les mêmes versions**. Le plus simple est de l'installer lui aussi avec packwiz (voir `HOSTING.md`).

## Étape 8 : ce que font les joueurs

Message à copier-coller aux amis :

> 1. Télécharge le zip **Interregne** et décompresse-le dans un dossier simple, par exemple `C:\Jeux\Interregne`. Évite le Bureau s'il est synchronisé par OneDrive.
> 2. Double-clique sur **`Jouer.bat`** (ou sur `prismlauncher.exe`). Si Windows affiche « Windows a protégé votre ordinateur », clique sur *Informations complémentaires* → *Exécuter quand même*.
> 3. **Première fois seulement :** Prism te demande un compte. Clique sur *Ajouter Microsoft*, ouvre le lien affiché, entre le code et connecte-toi avec le compte Microsoft qui possède Minecraft Java.
> 4. Relance **`Jouer.bat`**. Une fenêtre télécharge les mods (quelques minutes la première fois, quelques secondes ensuite), puis le jeu s'ouvre **directement sur le serveur**.
> 5. Les fois suivantes, il suffit de double-cliquer sur `Jouer.bat`. Les mises à jour sont automatiques.

À savoir :

- Chaque joueur doit posséder **Minecraft Java Edition** et se connecter **une fois** dans Prism. Le compte est mémorisé ensuite, mais seulement sur son PC : on ne partage jamais `accounts.json`.
- **Mac et Linux :** le zip est prévu pour Windows. Sur un autre système, installer Prism normalement, puis *Ajouter une instance* → *Importer* le dossier `instances/Interregne` zippé. Java est téléchargé automatiquement.

## Dépannage

- **« Failed to download » sur un mod CurseForge** : voir `MISSING_MODS.md` § 1.
- **Hash invalide sur tous les fichiers** : `index.toml` a sans doute été réécrit en CRLF. Relancer `packwiz refresh`, vérifier que `pack/.gitattributes` est bien commité, puis republier.
- **Pas de connexion automatique** : vérifier `JoinServerOnLaunchAddress` dans les paramètres de l'instance (onglet Serveur). Le serveur figure aussi dans « Multijoueur » grâce à `servers.dat`.
- **Le jeu manque de mémoire ou rame** : ajuster *Paramètres → Java → Mémoire maximale*. Si besoin, baisser la qualité de Distant Horizons en jeu.
