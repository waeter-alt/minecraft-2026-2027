<!-- [claude-build] notice envoyée avec Arene_Semaine_map.zip -->
# Arene Semaine : installation

Ce zip contient le monde `Arene Semaine` : un hub et 7 arènes, une par jour.

Il faut exactement ces versions :
- Minecraft Java 1.21.1
- NeoForge 21.1.216
- Create 6.0.9 pour 1.21.1 (fichier `create-1.21.1-6.0.9.jar`)

Le monde contient des blocs Create. Sans ce mod, ils disparaissent et l'arène Piège et Les Abysses sont abîmées. Ne l'ouvre pas sans Create.

## Prompt à donner à ton Claude

Copie tout le bloc ci-dessous dans Claude Code, sur ton PC, avec le zip déjà téléchargé.

```text
Aide-moi à installer un monde Minecraft moddé, sans toucher à mon installation Minecraft existante.

Versions exactes demandées : Minecraft Java 1.21.1, NeoForge 21.1.216, Create 6.0.9 pour Minecraft 1.21.1 (fichier create-1.21.1-6.0.9.jar).

1. Regarde d'abord ce que j'ai déjà : un launcher (Prism Launcher, CurseForge, Modrinth App...) et, si une instance NeoForge 1.21.1 avec Create existe, ses versions. Dis-moi ce que tu as trouvé avant de changer quoi que ce soit.
2. S'il n'y a pas d'instance adaptée, crée une instance séparée dédiée à ce monde. Si je n'ai pas de launcher adapté, propose-moi Prism Launcher depuis son site officiel, prismlauncher.org, et demande mon accord avant de l'installer.
3. Installe NeoForge 21.1.216 dans cette instance, depuis le launcher ou neoforged.net.
4. Installe Create 6.0.9 pour NeoForge 1.21.1 depuis Modrinth ou CurseForge, uniquement depuis la page officielle du mod Create. N'ajoute aucun autre mod sans me demander.
5. Demande-moi où se trouve Arene_Semaine_map.zip, puis extrais-le dans le dossier saves de cette instance. Le résultat doit être saves/Arene Semaine/level.dat, sans dossier en trop entre les deux.
6. Ne modifie jamais les fichiers du monde, et ne touche pas à mes autres instances ni à mon dossier .minecraft officiel.
7. Ne me demande jamais mes identifiants Microsoft : je me connecte moi-même dans le launcher.
8. Vérifie à la fin que le jar de Create est bien dans le dossier mods de l'instance et que level.dat est au bon endroit, puis dis-moi comment lancer l'instance et ouvrir le monde.

Si une version exacte n'est pas disponible, arrête-toi et dis-le-moi au lieu d'en choisir une autre.
```

## En jeu

- On arrive au hub. Chaque porte mène à l'arène de son jour, et une arche devant l'entrée ramène au hub. Mourir ramène aussi au hub.
- Pour un serveur à plusieurs : le serveur doit avoir NeoForge 21.1.216 et Create 6.0.9, chaque joueur aussi, et `enable-command-block=true` dans `server.properties`. Sans ça, les pièges, les portails et les portes du hub ne marchent pas.
- Ne pas utiliser de commande qui tue toutes les entités : elle détruirait la roue géante de l'arène Piège.
