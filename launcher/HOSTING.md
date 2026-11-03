# Héberger le serveur de L'Interrègne

Le cadre :

- 7 jours, de décembre 2026 à janvier 2027 ;
- 5 à 10 joueurs ;
- NeoForge 21.1.216 et environ 51 mods côté serveur, dont MineColonies (une colonie par faction, des dizaines de citoyens), Create, la série RPG et Distant Horizons avec génération des LOD côté serveur ;
- notre mod `interregne`.

> Prix indicatifs relevés fin 2026, TTC, à **revérifier au moment de commander**. Les gammes et tarifs changent souvent.

## 1. Besoins

| Ressource | Minimum | Recommandé | Pourquoi |
|---|---|---|---|
| RAM allouée à Java (`-Xmx`) | 8 Go | **10 à 12 Go** | Environ 4 à 5 Go au repos avec ce pack. MineColonies (citoyens, pathfinding, entrepôts), les chunks chargés autour de 10 joueurs dispersés et la génération Distant Horizons font monter la note. |
| RAM de la machine | 12 Go | **16 Go et plus** | Le heap, plus 2 à 3 Go pour la JVM hors heap, l'OS et la base SQLite de Distant Horizons. |
| CPU | 4 cœurs | **4 à 8 cœurs à fréquence élevée** | Le tick Minecraft tourne sur **un seul thread** : la performance mono-cœur compte plus que le nombre de cœurs. Un Ryzen 7000/9000 ou un EPYC récent est idéal. Les cœurs en plus servent à la génération de monde, aux workers de Distant Horizons, au GC et au chat vocal. |
| Disque | 20 Go SSD | 40 Go NVMe | Le monde, les données LOD de Distant Horizons (plusieurs Go) et les sauvegardes quotidiennes. |
| Réseau | 10 Mbit/s en upload | 50 Mbit/s et plus | Environ 0,5 à 1,5 Mbit/s par joueur, avec des pics lors de l'envoi des LOD Distant Horizons. Le vocal pèse environ 50 kbit/s par joueur. |
| Ports | 25565/TCP | et **24454/UDP** | Le port UDP sert à Simple Voice Chat. **Indispensable**, et pas toujours fourni par les hébergeurs de jeu. |

**Prégénérer le monde** avant l'ouverture : c'est le meilleur gain de performance pendant l'événement. Ajouter Chunky côté serveur (`packwiz modrinth add chunky`, puis `side = "server"`), prégénérer un rayon de 3 000 à 5 000 blocs, puis laisser Distant Horizons calculer ses LOD avant l'arrivée des joueurs.

## 2. Options

### A. PC perso et redirection de port (box)

- **Coût :** l'électricité seulement. Une machine de 150 à 250 W allumée 24 h/24 pendant 7 jours consomme 25 à 40 kWh, soit **environ 7 à 12 € la semaine**.
- **Pour :** gratuit, gros CPU de bureau (excellent en mono-cœur), contrôle total.
- **Contre :**
  - l'upload résidentiel est limité ;
  - certaines lignes sont en **CGNAT**, donc pas de redirection possible sans demander une IPv4 fixe au FAI ;
  - l'IP personnelle est exposée ;
  - le PC doit rester allumé et ne peut pas servir à jouer confortablement en même temps, sauf avec 32 Go et plus.
- **À faire :** rediriger 25565/TCP et 24454/UDP vers le PC, et réserver son IP locale (bail DHCP fixe).

### B. PC perso et tunnel playit.gg

- **Coût :** **gratuit**, ou **environ 3 $/mois** pour playit Premium (domaine personnalisé, meilleur routage, plus de tunnels).
- **Pour :** contourne le CGNAT et la box sans rien ouvrir, et masque l'IP perso. Il fournit un tunnel TCP pour le jeu **et** un tunnel UDP pour le vocal.
- **Contre :** quelques millisecondes de plus via le relais européen, et les mêmes limites qu'en A (PC allumé, upload).
- **Vocal :** le tunnel UDP a un port public différent. Renseigner `voice_host=<adresse playit>:<port>` dans `config/voicechat/voicechat-server.properties`.

### C. Hébergeur de jeu dédié

Exemples : location-minecraft.fr (utilisé en 2025), Minestrator, Nitroserv, PebbleHost, BisectHosting, Apex.

- **Coût :** **environ 2 à 4 € par Go de RAM et par mois**. Une offre de 12 Go revient à environ **25 à 45 €/mois**, souvent sans engagement.
- **Pour :**
  - panneau web et installation NeoForge en un clic ;
  - sauvegardes et anti-DDoS ;
  - support ;
  - zéro administration système.
- **Contre :**
  - le CPU est souvent partagé et rarement documenté : privilégier les offres « Ryzen 7950X/9950X » ;
  - **vérifier qu'un port UDP supplémentaire est fourni pour Simple Voice Chat** ;
  - certains panneaux limitent l'accès SSH/SFTP.

### D. VPS cloud

| Offre | Ressources | Prix indicatif | Remarque |
|---|---|---|---|
| OVHcloud VPS (France) | 8 vCores et 24 Go | **environ 15 à 20 €/mois** | Excellent rapport prix/RAM et faible latence depuis la France. vCPU partagés, facturation au mois. |
| Hetzner Cloud CCX23 | 4 vCPU **dédiés** et 16 Go | **environ 25 à 35 €/mois** | Facturé **à l'heure** : deux semaines coûtent environ la moitié. Datacenters en Allemagne et en Finlande (20 à 40 ms). |
| Hetzner Cloud CCX33 | 8 vCPU dédiés et 32 Go | **environ 50 à 65 €/mois** | Même chose, avec de la marge pour Distant Horizons. |
| Oracle Cloud « Always Free » (ARM) | 4 OCPU et 24 Go | 0 € | Gratuit, mais la capacité est souvent indisponible et le compte peut être fermé sans préavis. **Ne pas en dépendre** pour l'événement. |

- **Pour :** contrôle total (SSH, cron de sauvegarde, systemd), installation du pack par packwiz, ports libres (TCP et UDP).
- **Contre :** c'est à toi d'administrer le serveur : pare-feu, mises à jour, sauvegardes.

## 3. Recommandation

1. **Préparation et sessions de test, maintenant :** **option B**, ton PC et playit.gg en gratuit. Coût nul, et parfait pour tester le mod `interregne` avec quelques amis.
2. **Pendant l'événement :** un **VPS OVHcloud 8 vCores et 24 Go** (environ 15 à 20 € pour le mois).
   - Il est en France, il a une large marge de RAM pour 12 Go de heap plus Distant Horizons, et l'UDP du vocal est libre.
   - Le pack s'installe avec la même commande packwiz que les clients.
   - **Faire un test de charge 2 semaines avant**, avec 5 joueurs et des colonies actives, en surveillant le TPS via `/neoforge tps`.
   - Si le TPS chute à cause des vCPU partagés, basculer sur **Hetzner CCX33** (vCPU dédiés, facturé à l'heure, environ 15 à 20 € pour 10 jours).
3. **Plan B sans administration :** un hébergeur de jeu « Ryzen » de 12 Go (environ 30 à 45 €), à condition qu'il fournisse le port UDP du vocal.

## 4. Installer le serveur (VPS ou PC)

```bash
# Java 21 (Temurin) et un dossier dédié
mkdir -p /srv/interregne && cd /srv/interregne
# 1. NeoForge 21.1.216 (installeur sur neoforged.net)
java -jar neoforge-21.1.216-installer.jar --installServer
# 2. Mods du pack, côté serveur uniquement (les mods client sont exclus automatiquement)
java -jar packwiz-installer-bootstrap.jar -g -s server https://<compte>.github.io/interregne-pack/pack.toml
# 3. JVM : voir § 6, à mettre dans user_jvm_args.txt
echo "eula=true" > eula.txt
./run.sh nogui
```

- Relancer la commande packwiz **avant chaque démarrage** (dans le script systemd ou de démarrage), pour que le serveur reste aligné sur le pack.
- **Configs :** reprendre de 2025 les configs **serveur** utiles (`config/*-server.toml`, `config/*-common.toml`, `defaultconfigs/`). Prendre aussi `run/server/config/interregne-server.toml` du dépôt, en remettant les timers de production (la sauvegarde `.avant-tests` existe côté client de dev).
- **Sauvegardes :** un `tar` quotidien du dossier `world/` (avec `/save-off`, `/save-all flush`, puis `/save-on`), copié hors de la machine.

## 5. Mods installés côté serveur (51)

Ce sont les 55 mods du pack, moins les 4 mods client (Sodium, Iris Shaders, Team Capes, Enchantment Descriptions). Le shaderpack est exclu lui aussi. `packwiz-installer -s server` fait ce tri tout seul.

| Catégorie | Mods |
|---|---|
| Mod maison | L'Interrègne (`interregne`) |
| MineColonies (CurseForge) | MineColonies 1.1.1041, Structurize 1.0.793, BlockUI 1.0.205, Domum Ornamentum 1.0.223, Multi-Piston 1.2.58, TownTalk 1.2.0 |
| Série RPG et combat | Wizards, Paladins & Priests, Rogues & Warriors, Archers, Arsenal, Jewelry, Runes, Elemental Wizards, Gazebos (serveur seul), Spell Engine, Spell Power Attributes, More RPG Library, Critical Strike, Better Combat, Ranged Weapon API, Shield API, Bundle API, Structure Pool API, AzureLib Armor, playerAnimator, Accessories, oωo (owo-lib) |
| Contenu | Create 6.0.8, Sophisticated Backpacks, Sophisticated Core, Simple Planes, Small Ships, What Are They Up To (Watut), CoroUtil, Tree Harvester (serveur seul), Collective |
| Monde et carte | Distant Horizons 2.4.5, Xaero's Minimap, Xaero's World Map (partage du world-ID) |
| Outils et techniques | WorldEdit, Simple Voice Chat, JEI, AppleSkin (synchro de la saturation), Lithium, Forgified Fabric API, Architectury API, Cloth Config API, Bookshelf, Prickle |

Total : 1 + 6 + 22 + 9 + 3 + 10 = **51 mods serveur**, Gazebos et Tree Harvester compris (55 mods dans le pack, moins 4 mods client).

## 6. Flags JVM conseillés (`user_jvm_args.txt`)

Pour 10 à 12 Go de heap, prendre les flags d'Aikar (G1). C'est la référence pour les serveurs Minecraft sous Java 21.

```
-Xms10G
-Xmx10G
-XX:+UseG1GC
-XX:+ParallelRefProcEnabled
-XX:MaxGCPauseMillis=200
-XX:+UnlockExperimentalVMOptions
-XX:+DisableExplicitGC
-XX:+AlwaysPreTouch
-XX:G1NewSizePercent=30
-XX:G1MaxNewSizePercent=40
-XX:G1HeapRegionSize=8M
-XX:G1ReservePercent=20
-XX:G1HeapWastePercent=5
-XX:G1MixedGCCountTarget=4
-XX:InitiatingHeapOccupancyPercent=15
-XX:G1MixedGCLiveThresholdPercent=90
-XX:G1RSetUpdatingPauseTimePercent=5
-XX:SurvivorRatio=32
-XX:+PerfDisableSharedMem
-XX:MaxTenuringThreshold=1
-Dusing.aikars.flags=https://mcflags.emc.gs
-Daikars.new.flags=true
-Dfile.encoding=UTF-8
```

- **Au-delà de 12 Go de heap :** `G1NewSizePercent=40`, `G1MaxNewSizePercent=50`, `G1HeapRegionSize=16M`, `G1ReservePercent=15`, `InitiatingHeapOccupancyPercent=20`.
- **`-Xms` égal à `-Xmx`, avec `AlwaysPreTouch` :** la RAM est réservée dès le démarrage. Il faut donc que la machine ait réellement heap + 3 Go de libre.
- **Alternative avec 16 Go de heap ou plus et des cœurs libres :** `-XX:+UseZGC -XX:+ZGenerational` à la place du bloc G1 (pauses quasi nulles, un peu plus de CPU).
- **Distant Horizons côté serveur :** limiter ses threads de génération dans `config/DistantHorizons.toml`, à la moitié des cœurs au plus, pour qu'il ne vole pas le CPU au tick.
