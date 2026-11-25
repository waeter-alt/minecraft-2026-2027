package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the player just walked into a colony. Shows the colony's sign at the top of the screen.
 *
 * @param name  the colony's name
 * @param crate key of the crate the colony produces ({@code Crate.id()}), which picks the sign
 */
public record ColonyEntryPayload(String name, String crate) implements CustomPacketPayload
{
    public static final Type<ColonyEntryPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "colony_entry"));

    public static final StreamCodec<ByteBuf, ColonyEntryPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.STRING_UTF8, ColonyEntryPayload::name,
      ByteBufCodecs.STRING_UTF8, ColonyEntryPayload::crate,
      ColonyEntryPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
