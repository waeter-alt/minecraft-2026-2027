# Passation — atelier de build vanilla avec Claude Code

À copier-coller tel quel dans Claude Code, lancé dans un **dossier vide** (par exemple `Documents/minecraft-build`).

---

```text
Tu m'aides à construire dans Minecraft vanilla 1.21.1 (pas de mods). Tu génères des structures que je pose en jeu, et je transmets mes builds à un ami. Réponds-moi en français.

## Règles de travail (à écrire telles quelles dans un CLAUDE.md à la racine)
1. Environnement isolé : tout vit dans ce dossier. Tu ne touches JAMAIS à mon .minecraft officiel, ni à rien en dehors de ce dossier.
2. Fichiers flagués :
   - chaque fichier que tu crées porte une marque [claude-build] (en-tête en commentaire, ou une ligne dans MANIFEST.md pour les fichiers binaires comme les .nbt) ;
   - tu ne modifies ni ne supprimes jamais un fichier sans cette marque sans me demander ;
   - MANIFEST.md liste chaque fichier généré avec sa date et son rôle.
3. Tu travailles léger et séquentiellement : pas d'agents en parallèle, et tu ne suis pas les logs ligne à ligne (tu les lis une fois, à la fin).
4. Mes identifiants Microsoft : tu ne les vois jamais, c'est moi qui me connecte dans le launcher.
5. Avant de me montrer un résultat, tu le vérifies (le fichier se relit, la commande est juste).

## Mise en place (une seule fois)
1. Détecte mon OS. Crée l'arborescence :
   - launcher/ : Prism Launcher portable ;
   - sources/ : les scripts Python qui génèrent les structures ;
   - exports/ : les .nbt à transmettre ;
   - CLAUDE.md et MANIFEST.md.
2. Télécharge Prism Launcher (open source, uniquement depuis https://prismlauncher.org ou ses releases GitHub officielles) en version portable dans launcher/ :
   - Windows : le zip portable ;
   - macOS : l'app ;
   - Linux : l'AppImage.
   Demande-moi avant de télécharger.
3. Ouvre Prism. Guide-moi pour ajouter mon compte Microsoft (je le fais moi-même), puis pour créer une instance vanilla nommée « Build-1.21.1 » en version 1.21.1 exactement. Prism installe Java tout seul ; sinon, aide-moi.
4. Lance l'instance en ligne de commande (prismlauncher --launch "Build-1.21.1"). Guide-moi pour créer le monde « Atelier » :
   - mode Créatif ;
   - type de monde Superflat ;
   - commandes autorisées.
5. Installe nbtlib (pip) pour écrire les structures. Crée dans le monde le datapack :
   saves/Atelier/datapacks/atelier/pack.mcmeta (pack_format 48)
   saves/Atelier/datapacks/atelier/data/atelier/structure/
   Attention : en 1.21, le dossier s'appelle « structure », au singulier.

## Générer une structure (à chaque demande)
1. Écris un script dans sources/<nom>.py. Il produit un .nbt de structure vanilla, au format du fichier d'un bloc de structure :
   - DataVersion 3955 (pour la 1.21.1) ;
   - size, palette, blocks, entities.
   Le script écrit le .nbt à la fois dans le datapack et dans exports/.
2. Relis le .nbt pour vérifier : taille, nombre de blocs, palette.
3. Donne-moi les commandes à taper en jeu :
   /reload
   /place template atelier:<nom> ~ ~ ~
   Précise aussi l'orientation et où se placer.
4. J'ajuste avec mes retours et mes captures. Tu itères sur le script, sans repartir de zéro.

## Récupérer ce que je construis à la main
Pour sauvegarder mes builds, guide-moi avec le bloc de structure (/give @s structure_block, mode SAVE, 48×48×48 maximum par bloc). Si c'est plus grand, découpe en plusieurs morceaux nommés <nom>_1, <nom>_2…, avec les coordonnées relatives dans MANIFEST.md. Les fichiers sauvés arrivent dans saves/Atelier/generated/minecraft/structure/ : copie-les dans exports/.

## Transmettre à mon ami
Pour chaque build, prépare dans exports/ :
- le ou les .nbt ;
- une capture ;
- une ligne dans MANIFEST.md (dimensions, blocs principaux, comment le poser).
Je lui envoie le dossier.

## Fin de la mise en place
Quand Minecraft 1.21.1 est lancé et le monde Atelier ouvert, demande-moi : « Quelle structure veux-tu que je génère pour commencer ? »
```
