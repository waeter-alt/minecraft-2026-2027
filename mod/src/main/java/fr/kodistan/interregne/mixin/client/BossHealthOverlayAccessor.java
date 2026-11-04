package fr.kodistan.interregne.mixin.client;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

/**
 * The boss bars on screen: a construction site's bar tells the hut window how far the work is.
 */
@Mixin(value = BossHealthOverlay.class, remap = false)
public interface BossHealthOverlayAccessor
{
    @Accessor("events")
    Map<UUID, LerpingBossEvent> interregne$events();
}
