package fr.kodistan.interregne.commerce;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One trade: a seller colony offers some crates at a price to an allied colony; once the buyer accepts, a caravan
 * carries them along the road between the two. The price is held from the buyer until the crates arrive.
 */
public final class Contract
{
    public static final int OFFER = 0;
    public static final int TRANSIT = 1;
    /** The caravan has arrived but the buyer's warehouse could not take everything yet. */
    public static final int WAITING = 2;

    public final long id;
    public final int seller;
    public final int buyer;
    public final String crate;
    public final int quantity;
    public final long unitPrice;
    public int state = OFFER;
    /** What the buyer paid, kept until delivery. */
    public long escrow;
    /** The road, cell by cell, from the seller to the buyer. */
    public long[] path = new long[0];
    /** Index into the path, fractional between two cells. */
    public double progress;
    /** Crates not yet stored at the buyer's, once the caravan has arrived. */
    public int left;
    /** The mule. */
    @Nullable
    public UUID puppet;
    /** The man walking beside it. */
    @Nullable
    public UUID driver;
    /** The escort tier bought for this contract, 0 for none. */
    public int escort;
    /** What the seller paid for the escort, kept to refund an offer withdrawn before departure. */
    public long escortFromTreasury;
    public long escortFromPurse;
    /** Soldiers still alive: killed ones never come back, even when the caravan is hidden and shown again. */
    public int swordsmenLeft;
    public int archersLeft;
    /** The soldiers on the road now (not saved: they only exist while a player is near). */
    public final java.util.List<UUID> escorts = new java.util.ArrayList<>();
    /** A soldier is fighting: the caravan waits. Recomputed every tick. */
    public boolean engaged;

    public Contract(final long id, final int seller, final int buyer, final String crate, final int quantity, final long unitPrice)
    {
        this.id = id;
        this.seller = seller;
        this.buyer = buyer;
        this.crate = crate;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public long escortPaid()
    {
        return escortFromTreasury + escortFromPurse;
    }

    public long total()
    {
        return unitPrice * quantity;
    }

    public BlockPos cell(final int index)
    {
        return BlockPos.of(path[Math.clamp(index, 0, path.length - 1)]);
    }

    public CompoundTag save()
    {
        final CompoundTag tag = new CompoundTag();
        tag.putLong("id", id);
        tag.putInt("seller", seller);
        tag.putInt("buyer", buyer);
        tag.putString("crate", crate);
        tag.putInt("quantity", quantity);
        tag.putLong("unitPrice", unitPrice);
        tag.putInt("state", state);
        tag.putLong("escrow", escrow);
        tag.putLongArray("path", path);
        tag.putDouble("progress", progress);
        tag.putInt("left", left);
        tag.putInt("escort", escort);
        tag.putLong("escortTreasury", escortFromTreasury);
        tag.putLong("escortPurse", escortFromPurse);
        tag.putInt("swordsmen", swordsmenLeft);
        tag.putInt("archers", archersLeft);
        return tag;
    }

    public static Contract load(final CompoundTag tag)
    {
        final Contract contract = new Contract(tag.getLong("id"), tag.getInt("seller"), tag.getInt("buyer"), tag.getString("crate"), tag.getInt("quantity"), tag.getLong("unitPrice"));
        contract.state = tag.getInt("state");
        contract.escrow = tag.getLong("escrow");
        contract.path = tag.getLongArray("path");
        contract.progress = tag.getDouble("progress");
        contract.left = tag.getInt("left");
        // Saves from before the escort have none of these: tier 0
        contract.escort = tag.getInt("escort");
        contract.escortFromTreasury = tag.getLong("escortTreasury");
        contract.escortFromPurse = tag.getLong("escortPurse");
        contract.swordsmenLeft = tag.getInt("swordsmen");
        contract.archersLeft = tag.getInt("archers");
        return contract;
    }
}
