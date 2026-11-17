// [claude-build] Mode leste de la cape, dans les deux sens. Client -> serveur : le joueur demande a se lester ou a se delester (l'identifiant est ignore,
// le serveur prend le joueur qui envoie). Serveur -> client : etat retenu par le serveur (refus hors de l'eau, delestage automatique...).
package fr.atelier.grappin;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LestePayload(int idEntite, boolean actif) implements CustomPacketPayload {
    public static final Type<LestePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "leste"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LestePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LestePayload::idEntite,
            ByteBufCodecs.BOOL, LestePayload::actif,
            LestePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void traiter(LestePayload message, IPayloadContext contexte) {
        if (contexte.flow().isServerbound()) {
            contexte.enqueueWork(() -> {
                if (contexte.player() instanceof ServerPlayer joueur) {
                    CapeEvenements.demanderLest(joueur, message.actif());
                }
            });
        } else if (FMLEnvironment.dist.isClient()) {
            contexte.enqueueWork(() -> CapeClient.recevoirLest(message));
        }
    }
}
