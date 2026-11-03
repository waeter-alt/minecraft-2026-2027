package fr.kodistan.interregne.client.catalogue;

import com.ldtteam.structurize.storage.StructurePackMeta;

import java.util.HashMap;
import java.util.Map;

/**
 * French names and descriptions of the styles; packs we don't know keep their own text, without difficulty tags.
 */
public final class StyleNames
{
    private record Style(String name, String desc)
    {
    }

    private static final Map<String, Style> STYLES = new HashMap<>();

    private static final String MEDIEVAL = "Oyez ! Un nouveau château s'élève. Installez-vous près d'une rivière et d'une forêt pour bâtir votre ville médiévale. "
      + "L'hôtel de ville alternatif est un château, le classique un manoir. Certains bâtiments aiment être près de l'eau, et gardez de la place "
      + "pour l'université et la bibliothèque, qui vont bien ensemble.";

    static
    {
        put("Minecolonies Original", "Original", "Le style historique de MineColonies : simple, il couvre tous les plans essentiels.");
        put("Colonial", "Colonial", "Au début de l'ère coloniale, il mêle architecture néoclassique et premières touches industrielles.");
        put("Medieval Oak", "Médiéval en chêne", MEDIEVAL);
        put("Medieval Spruce", "Médiéval en sapin", MEDIEVAL);
        put("Medieval Birch", "Médiéval en bouleau", MEDIEVAL);
        put("Medieval Dark Oak", "Médiéval en chêne noir", MEDIEVAL);
        put("Urban Birch", "Bouleau urbain", "Idéal pour s'installer près d'une forêt de bouleaux, avec un look moderne.");
        put("Urban Savanna", "Savane urbaine", "Dans la chaleur de la savane, un style semi-moderne en acacia, plus ranch que ville.");
        put("Ancient Athens", "Athènes antique", "À l'âge d'Aristote et de Platon, une grande puissance méditerranéenne domine la mer Égée. "
          + "Fondez votre empire hellénistique et rivalisez avec la grande capitale grecque.");
        put("Desert Oasis", "Oasis du désert", "Les Mille et Une Nuits : chargez votre tapis volant et bâtissez votre empire du désert.");
        put("Shire", "La Comté", "Du mouton à manger, de la bière à boire et un bel arbre pour se reposer à l'ombre. Tirez une chaise : "
          + "le second petit-déjeuner approche.");
        put("Incan", "Inca", "Conquérez les Andes et faites grandir votre empire inca. Les bâtiments se posent sur les flancs des montagnes, voire dedans.");
        put("Stalactite Caves", "Grottes à stalactites", "Pour les amoureux des cavernes : bâtissez sous terre, votre bâtisseur s'occupe des stalactites.");
        put("Caledonia", "Calédonie", "Un beau mélange d'architecture coloniale britannique et française, niché dans le Pacifique Sud. "
          + "Un style urbain, fait pour les quartiers denses et les grands édifices.");
        put("Jungle Treehouse", "Cabanes de la jungle", "Vivez perché dans les grands arbres de la jungle (il faudra souvent les planter vous-même) "
          + "et reliez les bâtiments par des passerelles au-dessus de la canopée.");
        put("Pagoda", "Pagode", "Bâtissez votre empire de pagodes et conquérez toutes les forêts de cerisiers que vous trouverez.");
        put("Cavern", "Caverne", "Plongez dans les profondeurs avec ce style aux modules imbriqués. Si vous aimez les Lego, Tetris et l'ordre, "
          + "il est fait pour vous.");
        put("Fortress", "Forteresse", "Le rêve de tout monarque : une capitale parfaite, propre et ordonnée, sans trou où tomber "
          + "ni tas de terre à contourner.");
        put("Space Wars", "Guerres spatiales", "Installez-vous sur votre planète désertique préférée avec une devise : voir grand ou rentrer chez soi. "
          + "Un style gourmand en ressources, à bâtir dans un désert.");
        put("Lost Mesa City", "Cité perdue des mesas", "Relevez les ruines de la Cité perdue et rendez à l'Empire sa gloire d'antan.");
        put("Nordic Spruce", "Nordique en sapin", "Les Nordiques sont de retour et cherchent un chef. Fondez un empire nordique florissant "
          + "et faites trembler vos voisins.");
        put("Dark Oak Treehouse", "Cabanes en chêne noir", "Ces cabanes perchées et reliées entre elles se fondent dans la forêt de chênes noirs "
          + "tout en protégeant vos citoyens.");
        put("Warped Netherlands", "Terres biscornues du Nether", "Le style idéal pour fonder une colonie dans le Nether. Gare aux piglins zombifiés !");
    }

    private StyleNames()
    {
    }

    private static void put(final String pack, final String name, final String desc)
    {
        STYLES.put(pack, new Style(name, desc));
    }

    public static String name(final StructurePackMeta pack)
    {
        final Style style = STYLES.get(pack.getName());
        return style != null ? style.name() : pack.getName();
    }

    public static String desc(final StructurePackMeta pack)
    {
        final Style style = STYLES.get(pack.getName());
        return style != null ? style.desc() : pack.getDesc().replaceAll("\\[[^\\]]*\\]\\s*", "").trim();
    }
}
