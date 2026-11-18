// [claude-build] Propriete « couleur » du casier de vestiaire (valeurs de CasierLogique.COULEURS), pour varier les rangees du vestiaire.
package fr.atelier.grappin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

public enum CasierCouleur implements StringRepresentable {
    BLEU_CLAIR, CYAN, VERT, JAUNE, ORANGE;

    private final String nom = CasierLogique.COULEURS.get(ordinal());
    private final ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "textures/entity/casier_" + nom + ".png");

    @Override
    public String getSerializedName() {
        return nom;
    }

    public ResourceLocation texture() {
        return texture;
    }

    /** La couleur de ce nom (CasierLogique), ou null. */
    public static CasierCouleur deNom(String nom) {
        int i = nom == null ? -1 : CasierLogique.COULEURS.indexOf(nom);
        return i < 0 ? null : values()[i];
    }
}
