// [claude-build] Le levier de commande de la Station (le levier general du Poste, qui ouvre la bonde) : un LeverBlock (memes etats
// face/facing/powered, meme signal, meme clic que le levier de Minecraft, donc les memes tests du datapack), dessine par le code
// (LevierStationRendu, modele et animations de atelier/modeles/station/levier_station.bbmodel). Copie du levier de racine du Suspendu,
// avec son modele et sa texture a lui : le levier de racine n'en depend pas.
package fr.atelier.grappin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class LevierStation extends LeverBlock implements EntityBlock {
    public LevierStation(Properties proprietes) {
        super(proprietes);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState etat) {
        return new LevierStationEntite(pos, etat);
    }

    @Override
    protected RenderShape getRenderShape(BlockState etat) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }
}
