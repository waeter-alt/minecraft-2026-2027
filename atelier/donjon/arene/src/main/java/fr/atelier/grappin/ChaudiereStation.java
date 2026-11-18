// [claude-build] La chaudiere de la station : cuivre rivete, deux blocs de haut de modele, forme de 1,6 bloc (on ne se tient pas dans
// son dome). allumee : le foyer rougeoie (modele chaudiere_allumee, texture feu_allume lumineuse), le bloc eclaire et la cheminee fume.
package fr.atelier.grappin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class ChaudiereStation extends DecorStation {
    public static final BooleanProperty ALLUMEE = BooleanProperty.create("allumee");

    public ChaudiereStation(Properties proprietes) {
        super(proprietes, new double[][] {{1.5, 0, 1.5, 14.5, 26, 14.5}}, false);
        registerDefaultState(defaultBlockState().setValue(ALLUMEE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> constructeur) {
        super.createBlockStateDefinition(constructeur);
        constructeur.add(ALLUMEE);
    }

    @Override
    public void animateTick(BlockState etat, Level niveau, BlockPos pos, RandomSource alea) {
        if (etat.getValue(ALLUMEE) && !etat.getValue(WATERLOGGED) && alea.nextInt(3) == 0) {
            niveau.addParticle(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5 + alea.nextGaussian() * 0.05, pos.getY() + 2.05,
                    pos.getZ() + 0.5 + alea.nextGaussian() * 0.05, 0, 0.06, 0);
        }
    }
}
