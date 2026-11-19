// [claude-build] La douche du Vestiaire de la Station : pommeau mural. Clic droit : elle coule (propriete coule) ou s'arrete. Tant
// qu'elle coule, le serveur fait tomber des gouttes du pommeau tous les 2 ticks (pour tous les joueurs proches) et l'eau bruisse.
package fr.atelier.grappin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public class DoucheStation extends DecorStation {
    public static final BooleanProperty COULE = BooleanProperty.create("coule");
    /** Le centre du pommeau dans le modele nord : 1 pixel devant le centre du bloc, son dessous a 10 pixels de haut. */
    private static final double AVANCE = 1 / 16.0, HAUTEUR = 9.8 / 16.0;

    public DoucheStation(Properties proprietes) {
        super(proprietes, new double[][] {{5, 0, 4, 11, 16, 16}}, true);
        registerDefaultState(defaultBlockState().setValue(COULE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> constructeur) {
        super.createBlockStateDefinition(constructeur);
        constructeur.add(COULE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState etat, Level niveau, BlockPos pos, Player joueur, BlockHitResult coup) {
        if (!niveau.isClientSide) {
            boolean coule = !etat.getValue(COULE);
            niveau.setBlock(pos, etat.setValue(COULE, coule), Block.UPDATE_ALL);
            niveau.playSound(null, pos, coule ? SoundEvents.BUCKET_EMPTY : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6F, 1.3F);
            if (coule) {
                niveau.scheduleTick(pos, this, 1);
            }
        }
        return InteractionResult.sidedSuccess(niveau.isClientSide);
    }

    /** Posee deja ouverte (par une commande ou une structure) : elle coule tout de suite. */
    @Override
    protected void onPlace(BlockState etat, Level niveau, BlockPos pos, BlockState ancien, boolean deplace) {
        super.onPlace(etat, niveau, pos, ancien, deplace);
        if (!niveau.isClientSide && etat.getValue(COULE) && !(ancien.is(this) && ancien.getValue(COULE))) {
            niveau.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState etat, ServerLevel niveau, BlockPos pos, RandomSource alea) {
        if (!etat.getValue(COULE)) {
            return;
        }
        Direction avant = etat.getValue(FACING);
        double x = pos.getX() + 0.5 + avant.getStepX() * AVANCE, z = pos.getZ() + 0.5 + avant.getStepZ() * AVANCE;
        niveau.sendParticles(ParticleTypes.FALLING_WATER, x, pos.getY() + HAUTEUR, z, 5, 0.09, 0.0, 0.09, 0.0);
        if (alea.nextInt(20) == 0) {
            niveau.playSound(null, pos, SoundEvents.WEATHER_RAIN, SoundSource.BLOCKS, 0.25F, 1.6F);
        }
        niveau.scheduleTick(pos, this, 2);
    }
}
