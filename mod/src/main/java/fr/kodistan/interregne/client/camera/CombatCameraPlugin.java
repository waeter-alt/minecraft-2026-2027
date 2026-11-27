package fr.kodistan.interregne.client.camera;

import com.github.exopandora.shouldersurfing.api.client.event.ComputeTargetCameraOffsetEvent;
import com.github.exopandora.shouldersurfing.api.client.event.TickEvent;
import com.github.exopandora.shouldersurfing.api.event.IEventBus;
import com.github.exopandora.shouldersurfing.api.plugin.IShoulderSurfingPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Shoulder Surfing plugin (listed in shouldersurfing_plugin.json, loaded only by that client mod): the camera
 * steps back while the player is swinging, like the combat framing of third-person action games, so the
 * Better Combat sweep and nearby enemies stay in view. Sprint and aim framing come from the SSR config itself.
 * SSR eases the camera toward the target offset, so an on/off target is enough here.
 */
public class CombatCameraPlugin implements IShoulderSurfingPlugin {

    /** Ticks the pulled-back framing lingers after the last swing, so a combo doesn't make the camera pump. */
    private static final int HOLD_TICKS = 16;
    private static final double PULL_BACK = 0.7;
    private static final double RAISE = 0.1;
    /** After SSR's own handlers (sprint/aim modifiers, collision limits run on the default and lower priorities). */
    private static final int PRIORITY = 5000;

    private int holdTicks;

    @Override
    public void register(IEventBus eventBus) {
        eventBus.register((TickEvent event) -> tick());
        eventBus.register(PRIORITY, (ComputeTargetCameraOffsetEvent event) -> {
            if (holdTicks > 0) {
                event.setResult(event.getResult().add(0, RAISE, PULL_BACK));
            }
        });
    }

    private void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean swinging = mc.player != null && mc.screen == null && mc.options.keyAttack.isDown();
        if (swinging) {
            holdTicks = HOLD_TICKS;
        } else if (holdTicks > 0) {
            holdTicks--;
        }
    }
}
