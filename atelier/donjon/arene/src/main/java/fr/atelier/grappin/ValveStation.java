// [claude-build] La valve de la piscine du Donjon de la Station (Hall des Bassins) : une tuyauterie a volant, dessinee par le code (ValveStationRendu, modele et animation
// de atelier/modeles/station/valve_station.bbmodel). UN SEUL clic droit la tourne a fond (animation de trois tours) : sa propriete `tournee` passe a vrai et y reste,
// c'est ce que le datapack du donjon lit. Il la remet a faux a la remise a zero. La propriete `couleur` (rouge, bleue, verte, orange, cyan) choisit la peinture du volant. Elle peut etre noyee (waterlogged) : la valve de la pompe A est sous l'eau.
package fr.atelier.grappin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;

public class ValveStation extends Block implements EntityBlock, SimpleWaterloggedBlock {
    public static final BooleanProperty TOURNEE = BooleanProperty.create("tournee");
    public static final EnumProperty<Couleur> COULEUR = EnumProperty.create("couleur", Couleur.class);
    public static final BooleanProperty EAU = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape FORME = Block.box(2, 0, 2, 14, 15, 14);

    public enum Couleur implements StringRepresentable {
        ROUGE("rouge"), BLEU("bleu"), VERT("vert"), ORANGE("orange"), CYAN("cyan");

        private final String nom;

        Couleur(String nom) {
            this.nom = nom;
        }

        @Override
        public String getSerializedName() {
            return nom;
        }
    }

    public ValveStation(Properties proprietes) {
        super(proprietes);
        registerDefaultState(defaultBlockState().setValue(TOURNEE, false).setValue(COULEUR, Couleur.ROUGE).setValue(EAU, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> constructeur) {
        constructeur.add(TOURNEE, COULEUR, EAU);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext contexte) {
        return defaultBlockState().setValue(EAU, contexte.getLevel().getFluidState(contexte.getClickedPos()).getType() == Fluids.WATER);
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

    @Override
    protected VoxelShape getShape(BlockState etat, BlockGetter niveau, BlockPos pos, CollisionContext contexte) {
        return FORME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState etat, Level niveau, BlockPos pos, Player joueur, BlockHitResult coup) {
        if (etat.getValue(TOURNEE)) {
            return InteractionResult.CONSUME;                         // deja tournee a fond : rien de plus a faire
        }
        if (!niveau.isClientSide) {
            niveau.setBlock(pos, etat.setValue(TOURNEE, true), Block.UPDATE_ALL);
            niveau.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 0.6F);
            niveau.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.9F, 0.7F);
        }
        return InteractionResult.sidedSuccess(niveau.isClientSide);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState etat) {
        return new ValveStationEntite(pos, etat);
    }

    @Override
    protected RenderShape getRenderShape(BlockState etat) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }
}
