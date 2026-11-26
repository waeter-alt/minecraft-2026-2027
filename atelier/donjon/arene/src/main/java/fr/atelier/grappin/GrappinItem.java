// [claude-build] L'objet grappin (3) : clic droit lance le crochet ; il ne s'accroche qu'a un point d'ancrage et nous tire alors en ligne droite vers lui.
// Il faut RESTER APPUYE : relacher le clic droit lache le crochet (le client le demande, voir GrappinClient.Traction). Tant qu'un crochet existe, un clic ne fait rien.
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
        if (!level.isClientSide) {
            if (!Crochet.crochetsDe(joueur).isEmpty()) {    // un crochet est deja lance : on ne fait rien, il part quand on relache le clic
                return InteractionResultHolder.success(pile);
            }
            Crochet crochet = new Crochet(level, joueur);   // sinon on lance
            crochet.shootFromRotation(joueur, joueur.getXRot(), joueur.getYRot(), 0.0F, Reglages.VITESSE_CROCHET.get().floatValue(), 0.0F);
            level.addFreshEntity(crochet);
            level.playSound(null, joueur.getX(), joueur.getY(), joueur.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.8F, 0.6F);
            joueur.getCooldowns().addCooldown(this, Reglages.RECHARGE.get());
        }
        return InteractionResultHolder.sidedSuccess(pile, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack pile, TooltipContext contexte, List<Component> lignes, TooltipFlag drapeau) {
        lignes.add(Component.translatable("item.donjon.grappin.aide1"));
        lignes.add(Component.translatable("item.donjon.grappin.aide2"));
        lignes.add(Component.translatable("item.donjon.grappin.aide3"));
    }
}
