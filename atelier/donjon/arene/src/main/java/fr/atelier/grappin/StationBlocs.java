// [claude-build] Les blocs de decor du Donjon de la Station (station de pompage steampunk) : pompe, chaudiere, cadran mural.
// Modeles : atelier/modeles/station/*.bbmodel (atelier/scripts/station_decor_3d.py), exportes par station_decor_modeles.sh.
package fr.atelier.grappin;

import java.util.List;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
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

    public static final List<DeferredItem<BlockItem>> BLOCS_OBJETS = List.of(
            Grappin.ITEMS.registerSimpleBlockItem("pompe_station", POMPE),
            Grappin.ITEMS.registerSimpleBlockItem("chaudiere", CHAUDIERE),
            Grappin.ITEMS.registerSimpleBlockItem("cadran", CADRAN));

    /** Charge la classe (donc les inscriptions ci-dessus) avant que les registres du mod soient branches. */
    static void charger() {
    }
}
