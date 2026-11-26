// [claude-build] Les portes et barrages de la Station qui remplacent les grilles de bloc vanilla : la vanne (grille a lames dans un cadre de cuivre) et le barrage de planches
// (modeles : atelier/modeles/station/vanne_station.bbmodel et planche_station.bbmodel). Un bloc mince, dans le plan x, y (a plat) ou z, d'un bord a l'autre de la case, que l'eau traverse
// (propriete waterlogged). Collision = case entiere (plus d'interstice, meme sur un mur courbe). Le datapack du donjon les pose et les retire par commande ; la vanne est incassable. Le barrage de planches porte le tag donjon:cassable : la Cape
// lestee le brise d'un coup de tourbillon (CapeEvenements.tourbillon).
package fr.atelier.grappin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BarrageStation extends Block implements SimpleWaterloggedBlock {
    public static final EnumProperty<Direction.Axis> AXE = BlockStateProperties.AXIS;
    public static final BooleanProperty EAU = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape PLAN_X = Block.box(0, 0, 5.5, 16, 16, 10.5);
    private static final VoxelShape PLAN_Z = Block.box(5.5, 0, 0, 10.5, 16, 16);
    private static final VoxelShape PLAN_Y = Block.box(0, 5.5, 0, 16, 10.5, 16);

    public BarrageStation(Properties proprietes) {
        super(proprietes);
        registerDefaultState(defaultBlockState().setValue(AXE, Direction.Axis.X).setValue(EAU, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> constructeur) {
        constructeur.add(AXE, EAU);
    }

    @Override
    protected VoxelShape getShape(BlockState etat, BlockGetter niveau, BlockPos pos, CollisionContext contexte) {
        return switch (etat.getValue(AXE)) {
            case X -> PLAN_X;
            case Y -> PLAN_Y;
            case Z -> PLAN_Z;
        };
    }

    /** La collision est la case ENTIERE (le modele reste mince) : sur un mur courbe (les bouches de la Cuve), les plans de deux cases voisines ne se touchent pas, et on se glissait par les interstices. */
    @Override
    protected VoxelShape getCollisionShape(BlockState etat, BlockGetter niveau, BlockPos pos, CollisionContext contexte) {
        return Shapes.block();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext contexte) {
        FluidState fluide = contexte.getLevel().getFluidState(contexte.getClickedPos());
        Direction.Axis axe = contexte.getHorizontalDirection().getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        return defaultBlockState().setValue(AXE, axe).setValue(EAU, fluide.getType() == Fluids.WATER);
    }

    @Override
    protected FluidState getFluidState(BlockState etat) {
        return etat.getValue(EAU) ? Fluids.WATER.getSource(false) : super.getFluidState(etat);
    }

    @Override
    protected BlockState updateShape(BlockState etat, Direction direction, BlockState voisin, LevelAccessor niveau, BlockPos pos, BlockPos posVoisin) {
        if (etat.getValue(EAU)) {
            niveau.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(niveau));
        }
        return super.updateShape(etat, direction, voisin, niveau, pos, posVoisin);
    }
}
