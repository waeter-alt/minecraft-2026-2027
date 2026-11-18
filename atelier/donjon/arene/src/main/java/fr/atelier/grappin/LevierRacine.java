// [claude-build] Le levier de racine : un LeverBlock (memes etats face/facing/powered, meme signal, meme clic), mais dessine par le code
// (LevierRendu, modele et animations de atelier/modeles/donjon/levier_racine.bbmodel) au lieu d'un modele de bloc fixe.
package fr.atelier.grappin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class LevierRacine extends LeverBlock implements EntityBlock {
    public LevierRacine(Properties proprietes) {
        super(proprietes);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState etat) {
        return new LevierEntite(pos, etat);
    }

    @Override
    protected RenderShape getRenderShape(BlockState etat) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }
}
