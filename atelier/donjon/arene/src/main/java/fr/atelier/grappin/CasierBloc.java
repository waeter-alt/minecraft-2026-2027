// [claude-build] Le casier de vestiaire (Donjon de la Station) : un bloc de large sur deux de haut, comme une porte (half lower/upper).
// Le bas porte tout : l'inventaire de 27 cases (CasierEntite, NBT Items comme un coffre) et le dessin anime (CasierRendu) ; le haut
// n'est qu'une boite de collision qui renvoie le clic au bas. Pose par une commande (setblock du bas seul), le bas pose son haut lui-meme.
// Clic droit : la porte s'ouvre et l'inventaire aussi ; clic droit avec une teinture (bleu clair, cyan, vert, jaune, orange) : repeint.
package fr.atelier.grappin;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;

public class CasierBloc extends Block implements EntityBlock {
    public static final MapCodec<CasierBloc> CODEC = simpleCodec(CasierBloc::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<CasierCouleur> COULEUR = EnumProperty.create("couleur", CasierCouleur.class);

    public CasierBloc(Properties proprietes) {
        super(proprietes);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(OPEN, false).setValue(COULEUR, CasierCouleur.BLEU_CLAIR));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> constructeur) {
        constructeur.add(FACING, HALF, OPEN, COULEUR);
    }

    static BlockPos bas(BlockState etat, BlockPos pos) {
        return etat.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext contexte) {
        BlockPos pos = contexte.getClickedPos();
        Level niveau = contexte.getLevel();
        if (pos.getY() >= niveau.getMaxBuildHeight() - 1 || !niveau.getBlockState(pos.above()).canBeReplaced(contexte)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, contexte.getHorizontalDirection().getOpposite());
    }

    /** Le bas pose son haut, qu'il vienne d'un joueur, d'un setblock ou d'une structure (si la place est libre). */
    @Override
    protected void onPlace(BlockState etat, Level niveau, BlockPos pos, BlockState ancien, boolean deplace) {
        super.onPlace(etat, niveau, pos, ancien, deplace);
        if (niveau.isClientSide || ancien.is(this) || etat.getValue(HALF) != DoubleBlockHalf.LOWER) {
            return;
        }
        BlockState dessus = niveau.getBlockState(pos.above());
        if (!dessus.is(this) && dessus.canBeReplaced()) {
            niveau.setBlock(pos.above(), etat.setValue(HALF, DoubleBlockHalf.UPPER).setValue(OPEN, false), Block.UPDATE_ALL);
        }
    }

    /** Le haut disparait avec son bas ; le bas, qui tient l'inventaire, ne disparait jamais tout seul. */
    @Override
    protected BlockState updateShape(BlockState etat, Direction cote, BlockState voisin, LevelAccessor niveau, BlockPos pos, BlockPos posVoisin) {
        if (etat.getValue(HALF) == DoubleBlockHalf.UPPER && cote == Direction.DOWN
                && !(voisin.is(this) && voisin.getValue(HALF) == DoubleBlockHalf.LOWER)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(etat, cote, voisin, niveau, pos, posVoisin);
    }

    /** Casser le haut casse le bas (qui donne l'objet et vide l'inventaire au sol). */
    @Override
    public BlockState playerWillDestroy(Level niveau, BlockPos pos, BlockState etat, Player joueur) {
        if (!niveau.isClientSide && etat.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState dessous = niveau.getBlockState(pos.below());
            if (dessous.is(this) && dessous.getValue(HALF) == DoubleBlockHalf.LOWER) {
                niveau.destroyBlock(pos.below(), !joueur.isCreative(), joueur);
            }
        }
        return super.playerWillDestroy(niveau, pos, etat, joueur);
    }

    @Override
    protected void onRemove(BlockState etat, Level niveau, BlockPos pos, BlockState nouveau, boolean deplace) {
        Containers.dropContentsOnDestroy(etat, nouveau, niveau, pos);
        super.onRemove(etat, niveau, pos, nouveau, deplace);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState etat, Level niveau, BlockPos pos, Player joueur, BlockHitResult coup) {
        if (niveau.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (niveau.getBlockEntity(bas(etat, pos)) instanceof CasierEntite casier) {
            joueur.openMenu(casier);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack pile, BlockState etat, Level niveau, BlockPos pos, Player joueur, InteractionHand main,
                                              BlockHitResult coup) {
        if (!(pile.getItem() instanceof DyeItem teinture)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        CasierCouleur couleur = CasierCouleur.deNom(CasierLogique.couleurDeTeinture(teinture.getDyeColor().getSerializedName()));
        BlockPos bas = bas(etat, pos);
        BlockState etatBas = niveau.getBlockState(bas);
        if (couleur == null || !etatBas.is(this) || etatBas.getValue(COULEUR) == couleur) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!niveau.isClientSide) {
            niveau.setBlock(bas, etatBas.setValue(COULEUR, couleur), Block.UPDATE_ALL);
            BlockState haut = niveau.getBlockState(bas.above());
            if (haut.is(this)) {
                niveau.setBlock(bas.above(), haut.setValue(COULEUR, couleur), Block.UPDATE_ALL);
            }
            niveau.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            pile.consume(1, joueur);
        }
        return ItemInteractionResult.sidedSuccess(niveau.isClientSide);
    }

    /** Relance de ContainerOpenersCounter (comme le tonneau) : referme la porte si plus personne ne regarde dedans. */
    @Override
    protected void tick(BlockState etat, ServerLevel niveau, BlockPos pos, RandomSource alea) {
        if (niveau.getBlockEntity(pos) instanceof CasierEntite casier) {
            casier.verifierOuverture();
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState etat) {
        return etat.getValue(HALF) == DoubleBlockHalf.LOWER ? new CasierEntite(pos, etat) : null;
    }

    @Override
    protected RenderShape getRenderShape(BlockState etat) {
        return etat.getValue(HALF) == DoubleBlockHalf.LOWER ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState etat) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState etat, Level niveau, BlockPos pos) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(niveau.getBlockEntity(bas(etat, pos)));
    }

    @Override
    protected BlockState rotate(BlockState etat, Rotation rotation) {
        return etat.setValue(FACING, rotation.rotate(etat.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState etat, Mirror miroir) {
        return etat.rotate(miroir.getRotation(etat.getValue(FACING)));
    }

    @Override
    protected boolean isPathfindable(BlockState etat, PathComputationType type) {
        return false;
    }
}
