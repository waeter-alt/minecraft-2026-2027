# Serveur de playtest : à installer chez Luca

Dossier prêt à envoyer en FTP à la racine du serveur. Cadre : alpha 0.1.0, 2 joueurs (Duncan et Luca), monde créé de zéro, les colonies sont posées par les joueurs. Pour l'hébergement durable de l'événement, voir `../HOSTING.md`.

## Ce que contient le dossier

| Fichier | Rôle |
|---|---|
| `config/interregne-server.toml` | Profil playtest du mod : timers courts, trésor de départ 500 Couronnes, tribut de guerre 25 % du trésor. Fichier partiel, le mod complète le reste. |
| `config/minecolonies-server.toml` | Copie de la config MineColonies de Duncan (`mod/env/config/`). Point important : **raids de barbares coupés** (`dobarbariansspawn = false`). Sans ce fichier, MineColonies les laisse actifs. |
| `server.properties` | `online-mode=true` (comptes Microsoft), `spawn-protection=0`, `allow-flight=true`, `max-tick-time=-1`, 10 joueurs max. |
| `user_jvm_args.txt` | 6 Go de mémoire (flags Aikar). À monter à 8 Go si la machine de Luca a 16 Go ou plus. |

Rien à mettre dans `world/` : le monde se crée au premier démarrage. **Pas de `eula.txt`** : c'est à Luca de l'accepter lui-même (`eula=true`).

## Ordre d'installation

1. **NeoForge 21.1.216 exactement** (version figée du projet, ni plus récente ni plus ancienne) et **Java 21**. Si l'hébergeur propose un sélecteur de version, choisir cette version-là.
2. **Mods côté serveur.** Deux voies, selon ce que permet l'hébergeur :
   - *Il laisse lancer une commande ou un script* : depuis la racine du serveur, avant chaque démarrage,
     ```
     java -jar packwiz-installer-bootstrap.jar -g -s server https://raw.githubusercontent.com/waeter-alt/minecraft-2026-2027/main/launcher/pack/pack.toml
     ```
     (le `.jar` vient de `github.com/packwiz/packwiz-installer-bootstrap`). Il installe les mods serveur et le mod `interregne` 0.1.0-alpha, donjon compris, sans les mods client.
   - *FTP seulement* : Duncan lance cette même commande sur son PC dans un dossier vide, puis envoie le dossier `mods/` obtenu en FTP (59 jars, environ 230 Mo, sans les mods client). Le reste (`config/`, `server.properties`, `user_jvm_args.txt`) vient de ce dossier du dépôt. À refaire si le pack change (nouvelle version du mod `interregne`).
3. **Envoyer par FTP** à la racine du serveur : `server.properties`, `user_jvm_args.txt` et le dossier `config/` (il fusionne avec le `config/` existant, ne rien supprimer d'autre).
4. **Ports ouverts** : `25565/TCP` (jeu) et `24454/UDP` (chat vocal, Simple Voice Chat). Sans le UDP, le jeu marche mais pas la voix.
5. **Démarrer**, puis accepter l'EULA si le premier démarrage s'arrête dessus, et redémarrer.

## Vérifications au premier démarrage (2 minutes)

- Le log finit par `Done (…s)!` sans exception `fr.kodistan`. Un `NoSuchFile null1.blueprint` au premier Relais est du bruit connu.
- Ouvrir `config/interregne-server.toml` : il doit être **devenu long** (le mod a ajouté toutes les clés) et contenir `warTributePercent = 25` et `foundingTreasury = 500`, avec **aucune ligne `warTribute =`** (l'ancienne clé fixe).
- Dans `config/minecolonies-server.toml`, `dobarbariansspawn = false` est toujours là.
- S'il existe un dossier `world/serverconfig/` avec un `interregne-server.toml` dedans, il **prend le pas** sur `config/` : le supprimer.

## Avant la première partie

Luca se donne les droits : `op <son pseudo>` dans la console. Puis, une fois le monde créé (`mod/docs/plan-de-test-playtest.md`, section 0 bis) :

1. `/commerce empire poser` pose le Marché impérial à 6 blocs à l'est du spawn.
2. `/commerce empire marches` vérifie qu'il est connu.
3. `/couronnes frapper` donne des Couronnes de test à qui en veut.

Les joueurs lancent le jeu avec le launcher (pack 0.1.0-alpha) et tapent eux-mêmes l'adresse du serveur : il n'y a pas d'auto-join.

## Pour garder une trace

À la fin du playtest : sauvegarder le dossier `world/` (copie en FTP), puis, si le serveur doit continuer, remettre les timers de production en supprimant `config/interregne-server.toml` (voir l'en-tête du fichier).
