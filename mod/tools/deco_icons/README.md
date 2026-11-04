# Icônes des décorations

`gen.py` fabrique le nom et l'icône de chaque décoration du catalogue, style par style, pour qu'aucun style n'ait deux décorations avec la même icône ou le même nom.

```
python -I tools/deco_icons/gen.py libs/minecolonies-1.1.1041-1.21.1.jar build/moddev/artifacts/neoforge-21.1.216-client-extra-aka-minecraft-resources.jar <dossier du pack 1-bit_Pixel_Icons>
```

Il écrit `src/main/resources/assets/interregne/deco_icons.json` et une planche par style dans `textures/gui/deco/`. À relancer quand MineColonies change de version ou qu'on retouche les règles.

Dans l'ordre, pour chaque décoration :

1. une icône pixel art quand un mot de son nom a un sens connu (`RULES`) ;
2. l'objet Minecraft de `catalogue_names.json`, s'il n'est utilisé qu'une fois dans le style ;
3. un dessin tiré du plan lui-même : vu de dessus, de face pour les pièces fines, ou en coupe quand deux pièces se ressemblent vues de dessus.

Les pièces modulaires (routes, rails, murailles, canaux…) ont toujours la vue de dessus, avec le pictogramme de leur famille en coin.

## Crédits

Icônes pixel art : **1-bit Pixel Icons** par Nikoichu, https://nikoichu.itch.io/pixel-icons (licence CC0). Le pack n'est pas dans le dépôt ; seules les icônes utilisées sont recopiées, recolorées, dans les planches.
