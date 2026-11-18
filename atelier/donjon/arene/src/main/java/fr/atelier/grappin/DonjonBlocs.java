// [claude-build] Blocs et objets du Donjon Suspendu, au code couleur de ses portes (bleu = porte 1, jaune = porte 2, rouge = porte 3) :
// pierre gravee a runes et sceaux de pierre precieuse (les portes a cle), levier de racine (les enigmes du sous-sol 2), cles.
// Textures et modeles : atelier/scripts/donjon_blocs.py (levier et cles aussi dans atelier/modeles/donjon/*.bbmodel).
// Les portes et les sceaux sont incassables, comme la bedrock : sinon on contournerait les cles a la pioche.
package fr.atelier.grappin;

import java.util.List;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

public final class DonjonBlocs {
    private DonjonBlocs() {
    }

    private static BlockBehaviour.Properties incassable(MapColor couleur, int lumiere) {
        return BlockBehaviour.Properties.of().mapColor(couleur).strength(-1.0F, 3600000.0F).sound(SoundType.DEEPSLATE_BRICKS)
                .pushReaction(PushReaction.BLOCK).lightLevel(etat -> lumiere);
    }

    public static final DeferredBlock<Block> PORTE_BLEUE = Grappin.BLOCS.registerSimpleBlock("porte_bleue", incassable(MapColor.COLOR_BLUE, 4));
    public static final DeferredBlock<Block> PORTE_JAUNE = Grappin.BLOCS.registerSimpleBlock("porte_jaune", incassable(MapColor.COLOR_YELLOW, 4));
    public static final DeferredBlock<Block> PORTE_ROUGE = Grappin.BLOCS.registerSimpleBlock("porte_rouge", incassable(MapColor.COLOR_RED, 4));
    public static final DeferredBlock<Block> SCEAU_BLEU = Grappin.BLOCS.registerSimpleBlock("sceau_bleu", incassable(MapColor.COLOR_BLUE, 7));
    public static final DeferredBlock<Block> SCEAU_JAUNE = Grappin.BLOCS.registerSimpleBlock("sceau_jaune", incassable(MapColor.COLOR_YELLOW, 7));
    public static final DeferredBlock<Block> SCEAU_ROUGE = Grappin.BLOCS.registerSimpleBlock("sceau_rouge", incassable(MapColor.COLOR_RED, 7));

    /** Levier de racine : un levier de Minecraft (memes etats, meme signal), dont la boule d'ambre eclaire quand il est baisse. */
    public static final DeferredBlock<LeverBlock> LEVIER_RACINE = Grappin.BLOCS.register("levier_racine",
            () -> new LeverBlock(BlockBehaviour.Properties.of().noCollission().strength(0.5F).sound(SoundType.WOOD).pushReaction(PushReaction.DESTROY)
                    .lightLevel(etat -> etat.getValue(LeverBlock.POWERED) ? 6 : 0)));

    public static final List<DeferredItem<BlockItem>> BLOCS_OBJETS = List.of(
            Grappin.ITEMS.registerSimpleBlockItem("porte_bleue", PORTE_BLEUE),
            Grappin.ITEMS.registerSimpleBlockItem("porte_jaune", PORTE_JAUNE),
            Grappin.ITEMS.registerSimpleBlockItem("porte_rouge", PORTE_ROUGE),
            Grappin.ITEMS.registerSimpleBlockItem("sceau_bleu", SCEAU_BLEU),
            Grappin.ITEMS.registerSimpleBlockItem("sceau_jaune", SCEAU_JAUNE),
            Grappin.ITEMS.registerSimpleBlockItem("sceau_rouge", SCEAU_ROUGE),
            Grappin.ITEMS.registerSimpleBlockItem("levier_racine", LEVIER_RACINE));

    public static final DeferredItem<Item> CLE_BLEUE = Grappin.ITEMS.registerSimpleItem("cle_bleue", new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> CLE_JAUNE = Grappin.ITEMS.registerSimpleItem("cle_jaune", new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> CLE_ROUGE = Grappin.ITEMS.registerSimpleItem("cle_rouge", new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

    /** Charge la classe (donc les inscriptions ci-dessus) avant que les registres du mod soient branches. */
    static void charger() {
    }
}
