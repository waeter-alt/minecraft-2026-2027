// [claude-build] Bloc de decor du Donjon de la Station : oriente (facing), peut etre sous l'eau (waterlogged), forme donnee pour
// l'orientation nord et tournee pour les autres. Le modele (assets/grappin/models/block/station, exporte depuis atelier/modeles/station)
// peut deborder du bloc : la forme, elle, reste celle qu'on lui donne. Mural : se pose contre la face visee (le dos au mur).
package fr.atelier.grappin;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DecorStation extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private final Map<Direction, VoxelShape> formes;
    private final boolean mural;
    private final MapCodec<DecorStation> codec;

    /** formeNord : boites {x0, y0, z0, x1, y1, z1} en pixels, l'avant du bloc tourne vers le nord. */
    public DecorStation(Properties proprietes, double[][] formeNord, boolean mural) {
        super(proprietes);
        this.formes = tourner(formeNord);
        this.mural = mural;
        this.codec = simpleCodec(p -> new DecorStation(p, formeNord, mural));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
    }

    private static Map<Direction, VoxelShape> tourner(double[][] formeNord) {
        Map<Direction, VoxelShape> m = new EnumMap<>(Direction.class);
        for (Direction cote : Direction.Plane.HORIZONTAL) {
            VoxelShape forme = Shapes.empty();
            for (double[] b : formeNord) {
                double[] t = StationLogique.tourner(b, (int) (cote.toYRot() / 90 + 2));   // toYRot : sud 0, ouest 90, nord 180, est 270
                forme = Shapes.or(forme, Block.box(t[0], t[1], t[2], t[3], t[4], t[5]));
            }
            m.put(cote, forme);
        }
        return m;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> constructeur) {
        constructeur.add(FACING, WATERLOGGED);
    }

    @Override
    protected VoxelShape getShape(BlockState etat, BlockGetter niveau, BlockPos pos, CollisionContext contexte) {
        return formes.get(etat.getValue(FACING));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext contexte) {
        Direction face = contexte.getClickedFace();
        Direction regard = mural && face.getAxis().isHorizontal() ? face : contexte.getHorizontalDirection().getOpposite();
        boolean eau = contexte.getLevel().getFluidState(contexte.getClickedPos()).getType() == Fluids.WATER;
        return defaultBlockState().setValue(FACING, regard).setValue(WATERLOGGED, eau);
    }

    @Override
    protected FluidState getFluidState(BlockState etat) {
        return etat.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(etat);
    }

    @Override
    protected BlockState updateShape(BlockState etat, Direction cote, BlockState voisin, LevelAccessor niveau, BlockPos pos, BlockPos posVoisin) {
        if (etat.getValue(WATERLOGGED)) {
            niveau.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(niveau));
        }
        return super.updateShape(etat, cote, voisin, niveau, pos, posVoisin);
    }
}
