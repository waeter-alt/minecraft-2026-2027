// [claude-build] Les blocs de decor du Donjon de la Station (station de pompage steampunk) : pompe, chaudiere, cadran mural.
// Modeles : atelier/modeles/station/*.bbmodel (atelier/scripts/station_decor_3d.py), exportes par station_decor_modeles.sh.
package fr.atelier.grappin;

import java.util.List;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

public final class StationBlocs {
    private StationBlocs() {
    }

    public static final DeferredBlock<PompeStation> POMPE = Grappin.BLOCS.register("pompe_station",
            () -> new PompeStation(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).noOcclusion().strength(3.5F)
                    .sound(SoundType.COPPER).lightLevel(e -> e.getValue(PompeStation.EN_MARCHE) ? 7 : 0)));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PompeEntite>> POMPE_ENTITE = DonjonBlocs.BLOCS_ENTITES.register(
            "pompe_station", () -> BlockEntityType.Builder.of(PompeEntite::new, POMPE.get()).build(null));

    public static final DeferredBlock<ChaudiereStation> CHAUDIERE = Grappin.BLOCS.register("chaudiere",
            () -> new ChaudiereStation(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).noOcclusion().strength(3.5F)
                    .sound(SoundType.COPPER).lightLevel(e -> e.getValue(ChaudiereStation.ALLUMEE) ? 13 : 0)));

    public static final DeferredBlock<DecorStation> CADRAN = Grappin.BLOCS.register("cadran",
            () -> new DecorStation(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).noOcclusion().noCollission().strength(1.0F)
                    .sound(SoundType.COPPER), new double[][] {{3, 0, 12.5, 13, 13.5, 16}}, true));

    // Le Vestiaire (atelier/scripts/vestiaire_3d.py) : douche murale qui coule au clic droit, lavabo, banc qui se raccorde a ses voisins.
    public static final DeferredBlock<DoucheStation> DOUCHE = Grappin.BLOCS.register("douche",
            () -> new DoucheStation(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).noOcclusion().noCollission().strength(1.0F)
                    .sound(SoundType.METAL)));

    public static final DeferredBlock<DecorStation> LAVABO = Grappin.BLOCS.register("lavabo",
            () -> new DecorStation(BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).noOcclusion().strength(1.5F)
                    .sound(SoundType.STONE), new double[][] {{2, 8, 5, 14, 12, 15}, {6, 0, 10, 10, 8, 14}}, true));

    public static final DeferredBlock<BancStation> BANC = Grappin.BLOCS.register("banc",
            () -> new BancStation(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).noOcclusion().strength(1.5F)
                    .sound(SoundType.WOOD)));

    /** Le levier de commande du Poste (ouvre la bonde) : un levier de Minecraft (memes etats, meme signal) redessine ; voyant vert tire. */
    public static final DeferredBlock<LevierStation> LEVIER = Grappin.BLOCS.register("levier_station",
            () -> new LevierStation(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).noCollission().noOcclusion().strength(0.8F)
                    .sound(SoundType.METAL).pushReaction(PushReaction.DESTROY).lightLevel(e -> e.getValue(LeverBlock.POWERED) ? 4 : 0)));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LevierStationEntite>> LEVIER_ENTITE = DonjonBlocs.BLOCS_ENTITES.register(
            "levier_station", () -> BlockEntityType.Builder.of(LevierStationEntite::new, LEVIER.get()).build(null));

    public static final List<DeferredItem<BlockItem>> BLOCS_OBJETS = List.of(
            Grappin.ITEMS.registerSimpleBlockItem("pompe_station", POMPE),
            Grappin.ITEMS.registerSimpleBlockItem("chaudiere", CHAUDIERE),
            Grappin.ITEMS.registerSimpleBlockItem("cadran", CADRAN),
            Grappin.ITEMS.registerSimpleBlockItem("douche", DOUCHE),
            Grappin.ITEMS.registerSimpleBlockItem("lavabo", LAVABO),
            Grappin.ITEMS.registerSimpleBlockItem("banc", BANC),
            Grappin.ITEMS.registerSimpleBlockItem("levier_station", LEVIER));

    /** Charge la classe (donc les inscriptions ci-dessus) avant que les registres du mod soient branches. */
    static void charger() {
    }
}
