// [claude-build] Message client -> serveur : le joueur appuie sur ESPACE pour lacher son crochet. Le jeu n'envoie pas la touche de saut
// au serveur (hors vehicule), d'ou ce message. Le serveur ne supprime que les crochets accroches de ce joueur.
package fr.atelier.grappin;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LacherPayload() implements CustomPacketPayload {
    public static final Type<LacherPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "lacher"));
    public static final StreamCodec<ByteBuf, LacherPayload> CODEC = StreamCodec.unit(new LacherPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void traiter(LacherPayload message, IPayloadContext contexte) {
        contexte.enqueueWork(() -> {
            Player joueur = contexte.player();
            Crochet.crochetsDe(joueur).stream().filter(Crochet::estAccroche).forEach(Crochet::rappeler);
        });
    }
}
