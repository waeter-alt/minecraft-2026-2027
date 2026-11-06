package fr.kodistan.interregne.war;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.colonyEvents.EventStatus;
import com.minecolonies.api.colony.colonyEvents.IColonyRaidEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The colony event behind an assault. It exists because MineColonies' raiders only live attached to a real
 * {@code IColonyEntitySpawnEvent} — aiStep registers them with the event (eventID 0 or unknown event discards
 * the troop), and the stock RaiderWalkAI (march into buildings, fight) only runs with an event present.
 * Ours spawns no waves itself: {@link WarAssaults} spawns the légionnaires by hand at launch.
 * <p>
 * Not in MineColonies' event-type registry: after a save/reload MineColonies logs "missing registryEntry"
 * and drops the event, stranding its troops (memory-only state, a slice shortcut — /war stop cleans up).
 */
public class AssautEvent implements IColonyRaidEvent, INBTSerializable<CompoundTag>
{
    private static final ResourceLocation TYPE_ID = ResourceLocation.fromNamespaceAndPath("interregne", "assaut");

    private final IColony    colony;
    private final int        id;
    private       EventStatus status = EventStatus.STARTING;
    private       BlockPos   spawnPoint = BlockPos.ZERO;
    private final List<Entity> troops    = new ArrayList<>();

    public AssautEvent(final IColony colony)
    {
        this.colony = colony;
        this.id = colony.getEventManager().getAndTakeNextEventID();
    }

    @Override
    public EventStatus getStatus()
    {
        return status;
    }

    @Override
    public void setStatus(final EventStatus status)
    {
        this.status = status;
    }

    @Override
    public int getID()
    {
        return id;
    }

    @Override
    public ResourceLocation getEventTypeID()
    {
        return TYPE_ID;
    }

    @Override
    public void setColony(@NotNull final IColony colony)
    {
        // Fixed at construction; the manager's reload path calls this, so it is a no-op.
    }

    @Override
    public void onStart()
    {
        this.status = EventStatus.PROGRESSING;
    }

    /** The assault ends when its last troop is gone; the manager then removes this event. */
    @Override
    public void onUpdate()
    {
        troops.removeIf(entity -> !entity.isAlive() || entity.isRemoved());
        if (troops.isEmpty())
        {
            this.status = EventStatus.DONE;
        }
    }

    @Override
    public List<Entity> getEntities()
    {
        return troops;
    }

    @Override
    public void registerEntity(final Entity entity)
    {
        troops.add(entity);
    }

    @Override
    public void unregisterEntity(final Entity entity)
    {
        troops.remove(entity);
        if (troops.isEmpty())
        {
            this.status = EventStatus.DONE;
        }
    }

    @Override
    public EntityType<?> getNormalRaiderType()
    {
        return WarEntities.LEGIONNAIRE.get();
    }

    @Override
    public EntityType<?> getArcherRaiderType()
    {
        return WarEntities.LEGIONNAIRE.get();
    }

    @Override
    public EntityType<?> getBossRaiderType()
    {
        return WarEntities.LEGIONNAIRE.get();
    }

    @Override
    public void addSpawner(final BlockPos pos)
    {
        // No spawners: troops are spawned once, by hand.
    }

    @Override
    public List<BlockPos> getWayPoints()
    {
        return List.of(colony.getCenter());
    }

    @Override
    public void setSpawnPoint(final BlockPos spawnPoint)
    {
        this.spawnPoint = spawnPoint;
    }

    @Override
    public BlockPos getSpawnPos()
    {
        return spawnPoint;
    }

    @Override
    public CompoundTag serializeNBT(final HolderLookup.Provider provider)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("status", status.name());
        tag.putLong("spawn", spawnPoint.asLong());
        return tag;
    }

    @Override
    public void deserializeNBT(final HolderLookup.Provider provider, @NotNull final CompoundTag tag)
    {
        // Never actually loaded: the event type is not in MineColonies' registry (see class javadoc).
    }

    @Override
    public String toString()
    {
        return "AssautEvent#" + id + " " + status + " troops=" + troops.size();
    }
}
