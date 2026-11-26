// [claude-build] Un courant de la Station (collecteur de la Chaufferie, rampe de la Cheminee) : un bloc INVISIBLE, sans collision, qui garde l'eau de sa case (waterlogged) et dit
// dans quel sens (`sens`) et a quelle force (`vitesse`, en dixiemes de bloc par tick) l'eau pousse qui flotte dedans. La poussee est calculee chez le joueur (CourantClient), comme
// tout mouvement de joueur : elle est douce, alors que l'ancienne poussee du datapack le teleportait a chaque tick (saccades). Le bloc montre le courant : des filets de bulles
// qui partent dans le sens du courant (animateTick, cote client).
package fr.atelier.grappin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CourantStation extends Block implements SimpleWaterloggedBlock {
    public static final DirectionProperty SENS = BlockStateProperties.FACING;
    public static final IntegerProperty VITESSE = IntegerProperty.create("vitesse", 1, 9);
    public static final BooleanProperty EAU = BlockStateProperties.WATERLOGGED;

    public CourantStation(Properties proprietes) {
        super(proprietes);
        registerDefaultState(defaultBlockState().setValue(SENS, Direction.WEST).setValue(VITESSE, 5).setValue(EAU, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> constructeur) {
        constructeur.add(SENS, VITESSE, EAU);
    }

    @Override
    protected RenderShape getRenderShape(BlockState etat) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState etat, BlockGetter niveau, BlockPos pos, CollisionContext contexte) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState etat, BlockGetter niveau, BlockPos pos, CollisionContext contexte) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState etat, BlockGetter niveau, BlockPos pos) {
        return true;
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

    /** Le courant se voit : des bulles partent dans son sens (client seulement), d'autant plus vite que le courant est fort. */
    @Override
    public void animateTick(BlockState etat, Level niveau, BlockPos pos, RandomSource alea) {
        Direction sens = etat.getValue(SENS);
        double v = etat.getValue(VITESSE) / 10.0 * 0.6;
        for (int i = 0; i < 2; i++) {
            double x = pos.getX() + alea.nextDouble(), y = pos.getY() + alea.nextDouble(), z = pos.getZ() + alea.nextDouble();
            niveau.addParticle(ParticleTypes.BUBBLE, x, y, z, sens.getStepX() * v, sens.getStepY() * v, sens.getStepZ() * v);
        }
        if (alea.nextInt(3) == 0) {
            double x = pos.getX() + alea.nextDouble(), y = pos.getY() + alea.nextDouble(), z = pos.getZ() + alea.nextDouble();
            niveau.addParticle(ParticleTypes.BUBBLE_POP, x, y, z, sens.getStepX() * v * 0.5, sens.getStepY() * v * 0.5, sens.getStepZ() * v * 0.5);
        }
    }
}
