// [claude-build] Toucher un joueur en plein grappin le decroche : toute source de degats confondue (melee, fleche, magie, feu...), pour recompenser
// l'adresse de celui qui atteint une cible en mouvement. Pas de distinction par type de degats : plus simple a ecrire, a tester et a expliquer.
// La chute n'entre pas en jeu : elle est deja annulee tant que le joueur est tenu par la corde (voir Crochet).
package fr.atelier.grappin;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

@EventBusSubscriber(modid = Grappin.MODID)
public final class GrappinEvenements {
    private GrappinEvenements() {
    }

    @SubscribeEvent
    static void degats(LivingDamageEvent.Post evenement) {
        if (evenement.getNewDamage() > 0 && evenement.getEntity() instanceof ServerPlayer joueur) {
            Crochet.crochetsDe(joueur).stream().filter(Crochet::estAccroche).forEach(Crochet::rappeler);
        }
    }
}
