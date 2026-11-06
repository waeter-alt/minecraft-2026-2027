package fr.kodistan.interregne.mixin;

import com.minecolonies.core.colony.events.raid.RaidManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * The stock raider march (RaiderWalkAI) reads the raid history through getRandomBuilding, and an empty history
 * makes getLastRaid() null — a NullPointerException that idles the troop. An assault therefore seeds one entry.
 */
@Mixin(value = RaidManager.class, remap = false)
public interface RaidManagerAccessor
{
    @Accessor(value = "raidHistories", remap = false)
    List<RaidManager.RaidHistory> interregne$getRaidHistories();
}
