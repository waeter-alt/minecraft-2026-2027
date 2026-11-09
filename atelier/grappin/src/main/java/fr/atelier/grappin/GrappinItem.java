// [claude-build] L'objet grappin : clic droit lance le crochet. Accroche : un nouveau clic droit lache et relance aussitot
// (enchainement en plein vol). Crochet encore en vol : le clic droit le rappelle.
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
            List<Crochet> lances = Crochet.crochetsDe(joueur);
            boolean accroche = lances.stream().anyMatch(Crochet::estAccroche);
            lances.forEach(Crochet::rappeler);
            if (!lances.isEmpty() && !accroche) {          // crochet encore en vol : on le rappelle, c'est tout
                return InteractionResultHolder.success(pile);
            }
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
