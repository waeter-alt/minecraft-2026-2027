# MCP « playtest »

Permet à Claude de démarrer/arrêter le serveur de test (`mod/run/server`), d'y envoyer des commandes (rcon) et d'en lire les logs, pour mener des playtests multi sans manipulation manuelle.

Python 3 uniquement, aucune installation. Déclaré dans `.mcp.json` à la racine du repo : Claude Code propose de l'activer au premier lancement.

## Outils

| Outil | Rôle |
|---|---|
| `server_start` | Active rcon (mot de passe généré, écrit dans `run/server/server.properties`), lance `./gradlew runServer`, lève `.claude/testing.flag`, attend le « Done ». |
| `server_stop` | `stop` via rcon, arrêt forcé si besoin, retire `testing.flag`. |
| `server_status` | Un JSON compact : en cours / prêt / rcon / joueurs connectés. |
| `rcon` | Une commande serveur, réponse tronquée à 6000 caractères. |
| `tail_log` | Dernières lignes du log (80 max), filtre regex optionnel. |

## Prérequis

- Un monde dans `mod/run/server/world` (ex. `ColoniesV2`, voir `mod/docs/outils-de-test.md`). Le MCP refuse de démarrer sans monde plutôt que d'en créer un vide.
- Le client Minecraft fermé n'est **pas** nécessaire pour le serveur seul ; il l'est pour un `build` Gradle (verrou Windows).
- Pour jouer dessus avec ton client : `localhost:25565`, le serveur est en `online-mode=false`.

## Variables d'environnement (facultatives)

- `PLAYTEST_SERVER_DIR` : autre dossier de serveur (défaut `mod/run/server`).
- `PLAYTEST_RCON_PORT` : défaut `25575`.
