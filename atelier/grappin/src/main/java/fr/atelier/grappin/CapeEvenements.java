// [claude-build] La Cape lestee cote serveur : respiration illimitee sous l'eau, mode leste (attributs poses seulement tant qu'on est leste ET dans l'eau),
// et l'attaque tourbillon (tout est verifie ici : cape, eau, nage rapide, recharge, portee, degats). La nage rapide elle-meme est calculee chez le joueur (CapeClient).
package fr.atelier.grappin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Grappin.MODID)
public final class CapeEvenements {
    /** Ticks hors de l'eau toleres avant de delester tout seul (sauter au ras de la surface ne doit pas faire tout perdre). */
    private static final int GRACE_HORS_EAU = 20;

    // Valeurs choisies contre le code de nage vanilla (LivingEntity.travel) :
    // - efficacite de deplacement dans l'eau a 1 = vitesse et adherence de la marche terrestre ;
    // - vitesse de minage immerge x5 = 1, comme a l'air libre ;
    // - gravite x10 : dans l'eau le jeu n'en applique qu'1/16, soit 0,05/tick, plus que les 0,04 de poussee de la touche de saut : on coule (~4-5 blocs/s).
    private static final AttributeModifier LEST_MARCHE = new AttributeModifier(id("cape_lestee_lest_marche"), 1.0, AttributeModifier.Operation.ADD_VALUE);
    private static final AttributeModifier LEST_MINAGE = new AttributeModifier(id("cape_lestee_lest_minage"), 4.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    private static final AttributeModifier LEST_GRAVITE = new AttributeModifier(id("cape_lestee_lest_gravite"), 9.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

    private static final Set<UUID> LESTES = new HashSet<>();
    private static final Map<UUID, Integer> HORS_EAU = new HashMap<>();
    private static final Map<UUID, Long> DERNIER_TOURBILLON = new HashMap<>();

    private CapeEvenements() {
    }

    private static ResourceLocation id(String nom) {
        return ResourceLocation.fromNamespaceAndPath(Grappin.MODID, nom);
    }

    static boolean leste(Player joueur) {
        return LESTES.contains(joueur.getUUID());
    }

    /** Respiration illimitee : l'evenement tourne des deux cotes, la barre d'air ne bouge donc pas non plus chez le joueur. */
    @SubscribeEvent
    static void respiration(LivingBreatheEvent evenement) {
        if (evenement.getEntity() instanceof Player j && CapeLesteeItem.porte(j)) {
            evenement.setCanBreathe(true);
        }
    }

    /** Le joueur appuie sur la touche de lest. Se lester n'est possible que dans l'eau, avec la cape. */
    static void demanderLest(ServerPlayer j, boolean actif) {
        if (!actif) {
            delester(j, true);
            return;
        }
        if (leste(j)) {
            return;
        }
        if (!CapeLesteeItem.porte(j) || !j.isInWater() || j.isSpectator()) {
            PacketDistributor.sendToPlayer(j, new LestePayload(j.getId(), false));
            return;
        }
        LESTES.add(j.getUUID());
        HORS_EAU.remove(j.getUUID());
        appliquerAttributs(j, true);
        PacketDistributor.sendToPlayer(j, new LestePayload(j.getId(), true));
        j.displayClientMessage(Component.translatable("message.grappin.cape_lestee.leste").withStyle(ChatFormatting.DARK_AQUA), true);
        j.level().playSound(null, j.getX(), j.getY(), j.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 0.9F, 0.7F);
    }

    static void delester(ServerPlayer j, boolean prevenir) {
        HORS_EAU.remove(j.getUUID());
        appliquerAttributs(j, false);
        if (!LESTES.remove(j.getUUID())) {
            return;
        }
        PacketDistributor.sendToPlayer(j, new LestePayload(j.getId(), false));
        if (prevenir) {
            j.displayClientMessage(Component.translatable("message.grappin.cape_lestee.deleste").withStyle(ChatFormatting.AQUA), true);
            j.level().playSound(null, j.getX(), j.getY(), j.getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.PLAYERS, 0.8F, 1.2F);
        }
    }

    private static void appliquerAttributs(ServerPlayer j, boolean actif) {
        basculer(j, Attributes.WATER_MOVEMENT_EFFICIENCY, LEST_MARCHE, actif);
        basculer(j, Attributes.SUBMERGED_MINING_SPEED, LEST_MINAGE, actif);
        basculer(j, Attributes.GRAVITY, LEST_GRAVITE, actif);
    }

    private static void basculer(ServerPlayer j, Holder<Attribute> attribut, AttributeModifier modificateur, boolean actif) {
        AttributeInstance instance = j.getAttribute(attribut);
        if (instance == null) {
            return;
        }
        boolean present = instance.hasModifier(modificateur.id());
        if (actif && !present) {
            instance.addTransientModifier(modificateur);
        } else if (!actif && present) {
            instance.removeModifier(modificateur.id());
        }
    }

    @SubscribeEvent
    static void tick(PlayerTickEvent.Post evenement) {
        if (!(evenement.getEntity() instanceof ServerPlayer j) || !leste(j)) {
            return;
        }
        if (!CapeLesteeItem.porte(j) || !j.isAlive() || j.isSpectator()) {
            delester(j, true);
            return;
        }
        if (j.isInWater()) {
            HORS_EAU.remove(j.getUUID());
            appliquerAttributs(j, true);
        } else {
            // Hors de l'eau, le lest n'a aucun effet : on retire les attributs tout de suite, et on delest pour de bon apres un court delai.
            appliquerAttributs(j, false);
            if (HORS_EAU.merge(j.getUUID(), 1, Integer::sum) > GRACE_HORS_EAU) {
                delester(j, true);
            }
        }
    }

    /** Le joueur declenche l'attaque tourbillon. Tout est reverifie ici ; un refus est silencieux (le joueur n'a qu'a recliquer). */
    static void tourbillon(ServerPlayer j) {
        if (!CapeLesteeItem.porte(j) || !j.isAlive() || j.isSpectator() || !j.isInWater() || leste(j) || !(j.isSwimming() || j.isSprinting())) {
            return;
        }
        long maintenant = j.level().getGameTime();
        Long dernier = DERNIER_TOURBILLON.get(j.getUUID());
        if (!CapeCalcul.rechargee(maintenant, dernier == null ? -1 : dernier, Reglages.TOURBILLON_RECHARGE.get())) {
            return;
        }
        DERNIER_TOURBILLON.put(j.getUUID(), maintenant);

        double portee = Reglages.TOURBILLON_PORTEE.get();
        float degats = Reglages.TOURBILLON_DEGATS.get().floatValue();
        Vec3 centre = j.position().add(0, j.getBbHeight() / 2, 0);
        for (LivingEntity cible : j.level().getEntitiesOfClass(LivingEntity.class, j.getBoundingBox().inflate(portee))) {
            if (cible == j || !cible.isAlive() || cible.isSpectator() || !cible.isAttackable() || cible.isAlliedTo(j) || cible.getVehicle() == j) {
                continue;
            }
            Vec3 point = cible.position().add(0, cible.getBbHeight() / 2, 0);
            if (!CapeCalcul.aPortee(point.distanceToSqr(centre), portee)) {
                continue;
            }
            // ServerPlayer.hurt respecte deja le reglage PvP du serveur pour les cibles joueurs.
            if (degats > 0 && cible.hurt(j.damageSources().playerAttack(j), degats)) {
                Vec3 ecart = point.subtract(centre);
                cible.knockback(0.5, -ecart.x, -ecart.z);
            }
        }

        anneauDeBulles((ServerLevel) j.level(), centre, j.getLookAngle(), portee);
        j.level().playSound(null, j.getX(), j.getY(), j.getZ(), SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundSource.PLAYERS, 1.0F, 1.3F);
        j.level().playSound(null, j.getX(), j.getY(), j.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 0.7F);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(j, new TourbillonPayload(j.getId()));
    }

    /** Anneau de bulles dans le plan perpendiculaire a l'axe de nage, projetees vers l'exterieur. */
    private static void anneauDeBulles(ServerLevel monde, Vec3 centre, Vec3 axe, double portee) {
        Vec3 a = Math.abs(axe.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = axe.cross(a).normalize();
        Vec3 v = axe.cross(u).normalize();
        int n = 28;
        double rayon = Math.min(1.2, portee * 0.4);
        for (int i = 0; i < n; i++) {
            double t = i * Math.PI * 2 / n;
            Vec3 dir = u.scale(Math.cos(t)).add(v.scale(Math.sin(t)));
            Vec3 p = centre.add(dir.scale(rayon));
            monde.sendParticles(ParticleTypes.BUBBLE, p.x, p.y, p.z, 0, dir.x, dir.y, dir.z, 0.6);
            if (i % 3 == 0) {
                monde.sendParticles(ParticleTypes.BUBBLE_POP, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.02);
            }
        }
    }

    @SubscribeEvent
    static void deconnexion(PlayerEvent.PlayerLoggedOutEvent evenement) {
        if (evenement.getEntity() instanceof ServerPlayer j) {
            delester(j, false);
        }
        DERNIER_TOURBILLON.remove(evenement.getEntity().getUUID());
    }

    @SubscribeEvent
    static void changementDeMonde(PlayerEvent.PlayerChangedDimensionEvent evenement) {
        if (evenement.getEntity() instanceof ServerPlayer j) {
            delester(j, false);
        }
    }

    @SubscribeEvent
    static void reapparition(PlayerEvent.PlayerRespawnEvent evenement) {
        if (evenement.getEntity() instanceof ServerPlayer j) {
            delester(j, false);
        }
    }

    @SubscribeEvent
    static void mort(LivingDeathEvent evenement) {
        if (evenement.getEntity() instanceof ServerPlayer j) {
            delester(j, false);
        }
    }
}
