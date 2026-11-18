package fr.kodistan.interregne.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesRaider;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * {@code hurt} looks the raid event up through the colony as soon as a player or mob hits the raider. A troop
 * spawned by hand (creative egg, /summon: {@link fr.kodistan.interregne.war.AssaultTroop}) has no colony, so the
 * first blow crashed the server. Without a colony that whole branch is skipped, as it is on the client.
 */
@Mixin(value = AbstractEntityMinecoloniesRaider.class, remap = false)
public abstract class RaiderColonylessMixin
{
    @Shadow(remap = false)
    private IColony colony;

    @Redirect(method = "hurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isClientSide()Z"), remap = false)
    private boolean interregne$noColonyNoEventLookup(final Level level)
    {
        return level.isClientSide() || colony == null;
    }
}
