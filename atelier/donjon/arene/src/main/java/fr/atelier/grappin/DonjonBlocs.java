// [claude-build] Ce que le Donjon Suspendu ajoute au mod, au code couleur de ses portes (bleu = porte 1, jaune = porte 2, rouge = porte 3) :
// la porte (entite PorteDonjon, une dalle de pierre en arche qui monte), le levier de racine (bloc dessine par le code, LevierRendu)
// et les cles. Modeles et animations : atelier/modeles/donjon/*.bbmodel (atelier/scripts/donjon_3d.py, donjon_blocs.py).
// Le Donjon de la Station y ajoute le casier de vestiaire (CasierBloc, atelier/modeles/station/casier.bbmodel, atelier/scripts/casier_3d.py).
package fr.atelier.grappin;

import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DonjonBlocs {
    private DonjonBlocs() {
    }

    public static final DeferredRegister<BlockEntityType<?>> BLOCS_ENTITES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Grappin.MODID);

    /** Levier de racine : un levier de Minecraft (memes etats, meme signal), dessine par le code avec une vraie animation ; son cristal eclaire baisse. */
    public static final DeferredBlock<LevierRacine> LEVIER_RACINE = Grappin.BLOCS.register("levier_racine",
            () -> new LevierRacine(BlockBehaviour.Properties.of().noCollission().noOcclusion().strength(0.5F).sound(SoundType.WOOD)
                    .pushReaction(PushReaction.DESTROY).lightLevel(etat -> etat.getValue(LeverBlock.POWERED) ? 6 : 0)));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LevierEntite>> LEVIER_ENTITE = BLOCS_ENTITES.register("levier_racine",
            () -> BlockEntityType.Builder.of(LevierEntite::new, LEVIER_RACINE.get()).build(null));

    /** Casier de vestiaire (Donjon de la Station) : metal peint, deux blocs de haut, 27 cases ; la porte s'ouvre avec l'inventaire. */
    public static final DeferredBlock<CasierBloc> CASIER = Grappin.BLOCS.register("casier",
            () -> new CasierBloc(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).noOcclusion().strength(2.5F)
                    .sound(SoundType.METAL).pushReaction(PushReaction.BLOCK)));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CasierEntite>> CASIER_ENTITE = BLOCS_ENTITES.register("casier",
            () -> BlockEntityType.Builder.of(CasierEntite::new, CASIER.get()).build(null));

    /** La porte du donjon : immobile, posee dans l'arche par les fonctions du donjon, qui l'ouvrent en changeant sa donnee Ouverte. */
    public static final DeferredHolder<EntityType<?>, EntityType<PorteDonjon>> PORTE = Grappin.ENTITES.register("porte_donjon",
            () -> EntityType.Builder.<PorteDonjon>of(PorteDonjon::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("porte_donjon"));

    public static final List<DeferredItem<BlockItem>> BLOCS_OBJETS = List.of(
            Grappin.ITEMS.registerSimpleBlockItem("levier_racine", LEVIER_RACINE),
            Grappin.ITEMS.registerSimpleBlockItem("casier", CASIER));

    public static final DeferredItem<Item> CLE_BLEUE = Grappin.ITEMS.registerSimpleItem("cle_bleue", new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> CLE_JAUNE = Grappin.ITEMS.registerSimpleItem("cle_jaune", new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> CLE_ROUGE = Grappin.ITEMS.registerSimpleItem("cle_rouge", new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

    /** Charge la classe (donc les inscriptions ci-dessus) avant que les registres du mod soient branches, et branche le sien. */
    static void charger(IEventBus modBus) {
        StationBlocs.charger();
        BLOCS_ENTITES.register(modBus);
    }
}
