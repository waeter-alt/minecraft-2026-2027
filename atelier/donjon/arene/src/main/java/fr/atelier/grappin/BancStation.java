// [claude-build] Le banc du Vestiaire de la Station : lattes de bois clair sur deux traverses d'acier, haut d'une demi-dalle. L'assise
// court le long du bloc, de travers par rapport a facing. joint_g / joint_d : un banc de meme axe le prolonge de ce cote ; le modele
// n'y met alors pas de pieds, et une rangee de bancs se lit comme un seul long banc (pieds aux deux bouts seulement).
// Cote g = cote x = 0 du modele nord = facing.getCounterClockWise() ; cote d = facing.getClockWise().
package fr.atelier.grappin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class BancStation extends DecorStation {
    public static final BooleanProperty JOINT_G = BooleanProperty.create("joint_g");
    public static final BooleanProperty JOINT_D = BooleanProperty.create("joint_d");

    public BancStation(Properties proprietes) {
        super(proprietes, new double[][] {{0, 0, 2, 16, 8, 14}}, false);
        registerDefaultState(defaultBlockState().setValue(JOINT_G, false).setValue(JOINT_D, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> constructeur) {
        super.createBlockStateDefinition(constructeur);
        constructeur.add(JOINT_G, JOINT_D);
    }

    private boolean prolonge(BlockGetter niveau, BlockPos pos, Direction cote, Direction facing) {
        BlockState voisin = niveau.getBlockState(pos.relative(cote));
        return voisin.is(this) && voisin.getValue(FACING).getAxis() == facing.getAxis();
    }

    private BlockState joints(BlockState etat, BlockGetter niveau, BlockPos pos) {
        Direction f = etat.getValue(FACING);
        return etat.setValue(JOINT_G, prolonge(niveau, pos, f.getCounterClockWise(), f)).setValue(JOINT_D, prolonge(niveau, pos, f.getClockWise(), f));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext contexte) {
        BlockState etat = super.getStateForPlacement(contexte);
        return etat == null ? null : joints(etat, contexte.getLevel(), contexte.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState etat, Direction cote, BlockState voisin, LevelAccessor niveau, BlockPos pos, BlockPos posVoisin) {
        BlockState e = super.updateShape(etat, cote, voisin, niveau, pos, posVoisin);
        return cote.getAxis().isHorizontal() ? joints(e, niveau, pos) : e;
    }
}
