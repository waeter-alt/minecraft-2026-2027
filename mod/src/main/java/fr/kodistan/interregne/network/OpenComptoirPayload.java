package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: open (or refresh) a Comptoir régional's window.
 *
 * @param region      the colony's crate id, empty while unassigned.
 * @param nextSeconds seconds per crate at the next level, 0 at the top level.
 * @param upgradeCost Couronnes for the next level, -1 at the top level.
 * @param secondsLeft seconds until the next crate.
 */
public record OpenComptoirPayload(BlockPos comptoir, String colonyName, String region, int level, int seconds, int nextSeconds, long upgradeCost,
                                  int stock, int storage, boolean warehouse, int secondsLeft, long purse) implements CustomPacketPayload
{
    public static final Type<OpenComptoirPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "open_comptoir"));

    public static final StreamCodec<FriendlyByteBuf, OpenComptoirPayload> STREAM_CODEC = StreamCodec.ofMember(OpenComptoirPayload::write, OpenComptoirPayload::read);

    private static OpenComptoirPayload read(final FriendlyByteBuf buf)
    {
        return new OpenComptoirPayload(buf.readBlockPos(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(),
          buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarLong());
    }

    private void write(final FriendlyByteBuf buf)
    {
        buf.writeBlockPos(comptoir);
        buf.writeUtf(colonyName);
        buf.writeUtf(region);
        buf.writeVarInt(level);
        buf.writeVarInt(seconds);
        buf.writeVarInt(nextSeconds);
        buf.writeVarLong(upgradeCost);
        buf.writeVarInt(stock);
        buf.writeVarInt(storage);
        buf.writeBoolean(warehouse);
        buf.writeVarInt(secondsLeft);
        buf.writeVarLong(purse);
    }

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
