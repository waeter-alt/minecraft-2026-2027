package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: open (or refresh) a Banque régionale's window.
 *
 * @param upgradeCost price of the next level, or -1 when the bank is at its last level.
 * @param member      the player may deposit.
 * @param manager     the player may withdraw and upgrade (colony owner or officer).
 */
public record OpenRegionalBankPayload(BlockPos bank, String colonyKey, String colonyName, long balance, long capacity, int level,
                                      long upgradeCost, long nextCapacity, boolean member, boolean manager) implements CustomPacketPayload
{
    public static final Type<OpenRegionalBankPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "open_regional_bank"));

    public static final StreamCodec<FriendlyByteBuf, OpenRegionalBankPayload> STREAM_CODEC = StreamCodec.ofMember(OpenRegionalBankPayload::write, OpenRegionalBankPayload::read);

    private static OpenRegionalBankPayload read(final FriendlyByteBuf buf)
    {
        return new OpenRegionalBankPayload(buf.readBlockPos(), buf.readUtf(), buf.readUtf(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(),
          buf.readLong(), buf.readVarLong(), buf.readBoolean(), buf.readBoolean());
    }

    private void write(final FriendlyByteBuf buf)
    {
        buf.writeBlockPos(bank);
        buf.writeUtf(colonyKey);
        buf.writeUtf(colonyName);
        buf.writeVarLong(balance);
        buf.writeVarLong(capacity);
        buf.writeVarInt(level);
        buf.writeLong(upgradeCost);
        buf.writeVarLong(nextCapacity);
        buf.writeBoolean(member);
        buf.writeBoolean(manager);
    }

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
