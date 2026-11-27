package fr.kodistan.interregne.mixin;

import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesRaider;
import fr.kodistan.interregne.war.AssaultTroop;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raider habits still run by the base tick that make no sense for an assault troop ({@link AssaultTroop}), each one
 * switched off for the troops only (barbarians are untouched):
 * <ul>
 *   <li>the 30-minute despawn: a troop on a long march + siege died out of nowhere, lost without a fight;</li>
 *   <li>the "pass-through raid" flag set on every other colony whose land a raider crosses: our troops cross the
 *       attacker's and third parties' land, and raids are on in our config;</li>
 *   <li>the half-heart thorns sometimes thrown back at a player who hits hard.</li>
 * </ul>
 */
@Mixin(value = AbstractEntityMinecoloniesRaider.class, remap = false)
public abstract class AssaultTroopRaiderMixin
{
    @Inject(method = "shouldDespawn", at = @At("HEAD"), cancellable = true, remap = false)
    private void interregne$troopsNeverDespawn(final CallbackInfoReturnable<Boolean> cir)
    {
        if ((Object) this instanceof AssaultTroop)
        {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "onEnterChunk", at = @At("HEAD"), cancellable = true, remap = false)
    private void interregne$noPassThroughRaid(final ChunkPos pos, final CallbackInfo ci)
    {
        if ((Object) this instanceof AssaultTroop)
        {
            ci.cancel();
        }
    }

    @Redirect(method = "hurt", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"), remap = false)
    private boolean interregne$noThorns(final Entity attacker, final DamageSource source, final float amount)
    {
        return !((Object) this instanceof AssaultTroop) && attacker.hurt(source, amount);
    }
}
