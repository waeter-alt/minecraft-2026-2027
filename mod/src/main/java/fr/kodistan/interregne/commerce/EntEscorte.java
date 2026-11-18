package fr.kodistan.interregne.commerce;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * A soldier hired with a caravan's escort: a swordsman or an archer, in MineColonies' guard look, who walks beside the
 * caravan (the contract places him, see {@link Contracts}) and, as soon as a player at war with the seller's or the
 * buyer's colony comes near or strikes, drops formation and fights with ordinary goals until the threat is gone.
 * <p>
 * Not one of the war raiders ({@code EntLegionnaire}, {@code EntArcher}): those are bound to a colony and a raid event
 * (their constructor chain discards them without one, and their hit handling reads the event), and they are hostile to
 * every guard and citizen of the colonies the escort walks through.
 */
public class EntEscorte extends PathfinderMob implements RangedAttackMob
{
    private static final EntityDataAccessor<Integer> DATA_TIER = SynchedEntityData.defineId(EntEscorte.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ARCHER = SynchedEntityData.defineId(EntEscorte.class, EntityDataSerializers.BOOLEAN);

    /** Health by tier (index 0 is tier 1): a tier 1 escort of three is a fair fight for one player in iron. */
    private static final double[] HEALTH = {20, 24, 30, 36, 44};

    /** The colonies this caravan belongs to, to know who is at war with it. */
    private int seller;
    private int buyer;

    public EntEscorte(final EntityType<? extends PathfinderMob> type, final Level level)
    {
        super(type, level);
        this.setPersistenceRequired();
        this.setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder attributes()
    {
        return PathfinderMob.createMobAttributes()
                 .add(Attributes.MAX_HEALTH, 20)
                 .add(Attributes.ATTACK_DAMAGE, 1)
                 .add(Attributes.MOVEMENT_SPEED, 0.3)
                 .add(Attributes.FOLLOW_RANGE, 24)
                 .add(Attributes.ARMOR, 0);
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_TIER, 1);
        builder.define(DATA_ARCHER, false);
    }

    public int tier()
    {
        return entityData.get(DATA_TIER);
    }

    public boolean isArcher()
    {
        return entityData.get(DATA_ARCHER);
    }

    /**
     * Sets the soldier up for a contract: kind, gear and toughness from the tier, and the colonies he fights for.
     */
    public void equip(final int tier, final boolean archer, final int seller, final int buyer)
    {
        this.seller = seller;
        this.buyer = buyer;
        final int t = Escort.clamp(tier) < 1 ? 1 : Escort.clamp(tier);
        entityData.set(DATA_TIER, t);
        entityData.set(DATA_ARCHER, archer);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH[t - 1]);
        setHealth((float) HEALTH[t - 1]);
        // The material of the gear: 0 leather, 1 iron, 2 diamond, 3 netherite; archers are one step lighter
        final int[] swordsmanSet = {1, 1, 2, 2, 3};
        final int material = archer ? Math.max(0, swordsmanSet[t - 1] - 1) : swordsmanSet[t - 1];
        final boolean full = t >= 2;
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(armor(material, EquipmentSlot.HEAD)));
        setItemSlot(EquipmentSlot.CHEST, new ItemStack(armor(material, EquipmentSlot.CHEST)));
        if (full)
        {
            setItemSlot(EquipmentSlot.LEGS, new ItemStack(armor(material, EquipmentSlot.LEGS)));
            setItemSlot(EquipmentSlot.FEET, new ItemStack(armor(material, EquipmentSlot.FEET)));
        }
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(archer ? Items.BOW : sword(swordsmanSet[t - 1])));
        for (final EquipmentSlot slot : EquipmentSlot.values())
        {
            setDropChance(slot, 0);
        }
        registerKindGoals();
    }

    private static Item sword(final int material)
    {
        return switch (material)
        {
            case 3 -> Items.NETHERITE_SWORD;
            case 2 -> Items.DIAMOND_SWORD;
            default -> Items.IRON_SWORD;
        };
    }

    private static Item armor(final int material, final EquipmentSlot slot)
    {
        return switch (material)
        {
            case 0 -> switch (slot)
            {
                case HEAD -> Items.LEATHER_HELMET;
                case CHEST -> Items.LEATHER_CHESTPLATE;
                case LEGS -> Items.LEATHER_LEGGINGS;
                default -> Items.LEATHER_BOOTS;
            };
            case 1 -> switch (slot)
            {
                case HEAD -> Items.IRON_HELMET;
                case CHEST -> Items.IRON_CHESTPLATE;
                case LEGS -> Items.IRON_LEGGINGS;
                default -> Items.IRON_BOOTS;
            };
            case 2 -> switch (slot)
            {
                case HEAD -> Items.DIAMOND_HELMET;
                case CHEST -> Items.DIAMOND_CHESTPLATE;
                case LEGS -> Items.DIAMOND_LEGGINGS;
                default -> Items.DIAMOND_BOOTS;
            };
            default -> switch (slot)
            {
                case HEAD -> Items.NETHERITE_HELMET;
                case CHEST -> Items.NETHERITE_CHESTPLATE;
                case LEGS -> Items.NETHERITE_LEGGINGS;
                default -> Items.NETHERITE_BOOTS;
            };
        };
    }

    private boolean goalsSet;

    /**
     * Goals depend on the kind, which is only known once {@link #equip} ran.
     */
    private void registerKindGoals()
    {
        if (goalsSet)
        {
            return;
        }
        goalsSet = true;
        goalSelector.addGoal(0, new FloatGoal(this));
        if (isArcher())
        {
            goalSelector.addGoal(1, new RangedAttackGoal(this, 1.0, 20, 16.0F));
        }
        else
        {
            goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, true));
        }
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::wantsToFight));
    }

    /**
     * A player is a threat when he is at war with a colony of this caravan, and can be hurt.
     */
    private boolean wantsToFight(final LivingEntity who)
    {
        return who instanceof Player player && !player.isCreative() && !player.isSpectator() && level() instanceof ServerLevel server
                 && Contracts.hostile(server, player, seller, buyer);
    }

    /**
     * Whether he is busy with an enemy: the caravan stops and he fights where he stands.
     */
    public boolean engaged()
    {
        final LivingEntity target = getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void performRangedAttack(final LivingEntity target, final float distanceFactor)
    {
        final ItemStack bow = getMainHandItem();
        final AbstractArrow arrow = ProjectileUtil.getMobArrow(this, new ItemStack(Items.ARROW), distanceFactor, bow);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.setBaseDamage(arrow.getBaseDamage() + tier() * 0.5);
        final double dx = target.getX() - getX();
        final double dy = target.getY(0.3333333333333333) - arrow.getY();
        final double dz = target.getZ() - getZ();
        final double flat = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + flat * 0.2, dz, 1.6F, 14 - level().getDifficulty().getId() * 4);
        playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (getRandom().nextFloat() * 0.4F + 0.8F));
        level().addFreshEntity(arrow);
    }

    @Override
    public boolean canBeLeashed()
    {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(final double distanceToClosestPlayer)
    {
        return false;
    }

    @Override
    public void addAdditionalSaveData(final CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putInt("tier", tier());
        tag.putBoolean("archer", isArcher());
    }

    @Override
    public void readAdditionalSaveData(final CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_TIER, Math.max(1, tag.getInt("tier")));
        entityData.set(DATA_ARCHER, tag.getBoolean("archer"));
    }
}
