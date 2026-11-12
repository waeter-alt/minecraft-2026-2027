// [claude-build] L'objet grappin : clic droit lance le crochet. Tant qu'un crochet est en jeu (en vol ou accroche), le clic droit ne
// fait plus rien ici : maintenu, il sert de traction (GrappinClient) ; on lache avec ESPACE ou en s'accroupissant.
package fr.atelier.grappin;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class GrappinItem extends Item {
    public GrappinItem(Properties proprietes) {
        super(proprietes);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player joueur, InteractionHand main) {
        ItemStack pile = joueur.getItemInHand(main);
        if (!Crochet.crochetsDe(joueur).isEmpty()) {      // vrai des deux cotes : le clic maintenu ne relance rien, et pas de geste de bras
            return InteractionResultHolder.fail(pile);
        }
        if (!level.isClientSide) {
            Crochet crochet = new Crochet(level, joueur);
            crochet.shootFromRotation(joueur, joueur.getXRot(), joueur.getYRot(), 0.0F, Reglages.VITESSE_CROCHET.get().floatValue(), 0.0F);
            level.addFreshEntity(crochet);
            level.playSound(null, joueur.getX(), joueur.getY(), joueur.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.8F, 0.6F);
            joueur.getCooldowns().addCooldown(this, Reglages.RECHARGE.get());
        }
        return InteractionResultHolder.sidedSuccess(pile, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack pile, TooltipContext contexte, List<Component> lignes, TooltipFlag drapeau) {
        lignes.add(Component.translatable("item.grappin.grappin.aide1"));
        lignes.add(Component.translatable("item.grappin.grappin.aide2"));
        lignes.add(Component.translatable("item.grappin.grappin.aide3"));
    }
}
