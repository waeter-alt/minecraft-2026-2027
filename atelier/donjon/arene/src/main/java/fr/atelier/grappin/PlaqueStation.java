// [claude-build] La plaque de pression lestee de la Station (secteur B, la Chaufferie) : un bloc plein dont le dessus dit qu'on doit aller dessus (cuivre a bordure jaune et noire,
// voyant ambre) ; le datapack la passe a `active` quand quelqu'un s'y tient debout : le voyant devient vert et le bloc eclaire. Modeles : atelier/modeles/station/plaque_station.bbmodel.
package fr.atelier.grappin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class PlaqueStation extends Block {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public PlaqueStation(Properties proprietes) {
        super(proprietes);
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> constructeur) {
        constructeur.add(ACTIVE);
    }
}
