package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: open the Trône impérial window. Intendants get the edict controls; everyone sees the tithe.
 *
 * @param weight      the tithe's weight, as a TitheWeight ordinal.
 * @param secondsLeft seconds before the current tithe is due; meaningless when none is running.
 * @param autopilot   no Intendant is online, the Empire runs itself.
 * @param colonyName  the player's colony, empty when they have none.
 * @param basket      what the colony's tithe asks, by crate ordinal; owed what it still has to deliver.
 */
public record OpenThronePayload(BlockPos throne, boolean intendant, int weight, boolean titheActive, long secondsLeft, boolean autopilot, String colonyName,
                                int[] basket, int[] owed, long treasury) implements CustomPacketPayload
{
    public static final Type<OpenThronePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "open_throne"));

    public static final StreamCodec<FriendlyByteBuf, OpenThronePayload> STREAM_CODEC = StreamCodec.ofMember(OpenThronePayload::write, OpenThronePayload::read);

    private static OpenThronePayload read(final FriendlyByteBuf buf)
    {
        return new OpenThronePayload(buf.readBlockPos(), buf.readBoolean(), buf.readVarInt(), buf.readBoolean(), buf.readVarLong(), buf.readBoolean(), buf.readUtf(),
          buf.readVarIntArray(), buf.readVarIntArray(), buf.readVarLong());
    }

    private void write(final FriendlyByteBuf buf)
    {
        buf.writeBlockPos(throne);
        buf.writeBoolean(intendant);
        buf.writeVarInt(weight);
        buf.writeBoolean(titheActive);
        buf.writeVarLong(secondsLeft);
        buf.writeBoolean(autopilot);
        buf.writeUtf(colonyName);
        buf.writeVarIntArray(basket);
        buf.writeVarIntArray(owed);
        buf.writeVarLong(treasury);
    }

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
