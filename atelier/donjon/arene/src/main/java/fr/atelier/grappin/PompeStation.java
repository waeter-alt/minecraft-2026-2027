// [claude-build] La pompe de la station : grosse pompe centrifuge dont le modele deborde (2,75 x 2 x 1,9 blocs) sur un seul bloc de
// collision. en_marche : voyant vert allume (modele pompe_station_marche) et volant qui tourne (PompeRendu, via PompeEntite).
package fr.atelier.grappin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class PompeStation extends DecorStation implements EntityBlock {
    public static final BooleanProperty EN_MARCHE = BooleanProperty.create("en_marche");

    public PompeStation(Properties proprietes) {
        super(proprietes, new double[][] {{0, 0, 0, 16, 16, 16}}, false);
        registerDefaultState(defaultBlockState().setValue(EN_MARCHE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> constructeur) {
        super.createBlockStateDefinition(constructeur);
        constructeur.add(EN_MARCHE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState etat) {
        return new PompeEntite(pos, etat);
    }
}
