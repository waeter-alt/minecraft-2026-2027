package fr.kodistan.interregne.mixin.client;

import fr.kodistan.interregne.client.walls.WallPlanner;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While the wall planner is open, the camera leaves the player and hovers above the colony.
 */
@Mixin(Camera.class)
public abstract class CameraMixin
{
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Inject(method = "setup", at = @At("TAIL"))
    private void interregne$aerialView(final BlockGetter level, final Entity entity, final boolean detachedIn, final boolean mirror,
      final float partialTick, final CallbackInfo ci)
    {
        if (WallPlanner.active())
        {
            final Vec3 position = WallPlanner.cameraPosition();
            setRotation(WallPlanner.yaw(), WallPlanner.pitch());
            setPosition(position.x, position.y, position.z);
            detached = true;
        }
    }
}
