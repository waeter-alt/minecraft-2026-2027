package fr.kodistan.interregne.garrison;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.Skill;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Troop stats follow the hut that trains them: upgrade the academy or the archery and every troop already bought takes
 * the new level (health, armor, skills, model). Checked every 5 seconds, which also covers respawns and chunk loads.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class GarrisonStats
{
    private static final ResourceLocation HEALTH = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "troop_health");
    private static final ResourceLocation ARMOR = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "troop_armor");

    private GarrisonStats()
    {
    }

    @SubscribeEvent
    public static void onTick(final ServerTickEvent.Post event)
    {
        if (event.getServer().getTickCount() % 100 != 0)
        {
            return;
        }
        final GarrisonRoster roster = GarrisonRoster.get(event.getServer());
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
            {
                final GarrisonRoster.Guard guard = roster.of(colony, citizen);
                if (guard != null)
                {
                    citizen.getEntity().ifPresent(entity -> apply(roster, colony, citizen, entity, guard));
                }
            }
        }
    }

    private static void apply(final GarrisonRoster roster, final IColony colony, final ICitizenData citizen, final AbstractEntityCitizen entity,
      final GarrisonRoster.Guard guard)
    {
        final int level = Garrison.levelOf(colony, guard.kind());
        set(entity, Attributes.MAX_HEALTH, HEALTH, Garrison.health(guard.kind(), level));
        set(entity, Attributes.ARMOR, ARMOR, Garrison.armor(guard.kind(), level));
        if (entity.getHealth() > entity.getMaxHealth())
        {
            entity.setHealth(entity.getMaxHealth());
        }
        for (final Skill skill : Skill.values())
        {
            final int missing = Garrison.skill(level) - citizen.getCitizenSkillHandler().getLevel(skill);
            if (missing > 0)
            {
                citizen.getCitizenSkillHandler().incrementLevel(skill, missing);
            }
        }
        if (guard.level() != level)
        {
            roster.setLevel(colony, citizen, level);
            PacketDistributor.sendToPlayersTrackingEntity(entity, new GuardLookPayload(entity.getId(), guard.kind(), level));
        }
    }

    /**
     * Brings the attribute to exactly the target through our own modifier, whatever MineColonies set as base.
     */
    private static void set(final AbstractEntityCitizen entity, final Holder<Attribute> attribute, final ResourceLocation id, final double target)
    {
        final AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null)
        {
            return;
        }
        final AttributeModifier current = instance.getModifier(id);
        final double without = instance.getValue() - (current == null ? 0 : current.amount());
        final double amount = target - without;
        if (current != null && Math.abs(current.amount() - amount) < 1.0E-4)
        {
            return;
        }
        instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
    }
}
