package fr.kodistan.interregne.garrison;

import fr.kodistan.interregne.InterregneMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: this citizen entity is a bought guard, drawn as our légionnaire or archère of that troop level.
 */
public record GuardLookPayload(int entityId, int kind, int level) implements CustomPacketPayload
{
    public static final Type<GuardLookPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "guard_look"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GuardLookPayload> STREAM_CODEC = StreamCodec.of(
      (buf, p) -> {
          buf.writeVarInt(p.entityId);
          buf.writeVarInt(p.kind);
          buf.writeVarInt(p.level);
      },
      buf -> new GuardLookPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
