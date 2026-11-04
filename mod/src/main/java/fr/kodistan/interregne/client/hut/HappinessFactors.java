package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Our plain-French reading of MineColonies' happiness factors: what raises or lowers this citizen's happiness, and
 * what to do about it. Every sentence follows the factor's code (CitizenHappinessHandler,
 * ModHappinessFactorTypeInitializer); factors we do not know fall back to MineColonies' own text.
 */
public final class HappinessFactors
{
    /**
     * A factor for this citizen: its name, why it is up or down, and what to do (null when nothing is to be done).
     */
    public record Reading(String name, String why, @Nullable String fix)
    {
    }

    private static final String NAME_KEY = "com.minecolonies.coremod.gui.townhall.happiness.";
    private static final String DESC_KEY = "com.minecolonies.coremod.gui.townhall.happiness.desc.";

    private HappinessFactors()
    {
    }

    public static Reading read(final String id, final boolean good, final ICitizenDataView citizen, final IColonyView colony)
    {
        final IBuildingView home = citizen.getHomeBuilding() == null ? null : colony.getBuilding(citizen.getHomeBuilding());
        final int homeLevel = home == null ? 0 : home.getBuildingLevel();
        return switch (id)
        {
            case "security" -> good
              ? new Reading("Sécurité", "La colonie est bien gardée.", null)
              : new Reading("Sécurité", "Trop peu de gardes : il en faut environ 2 pour 3 autres habitants.", "Recrutez des gardes.");
            case "social" -> good
              ? new Reading("Ambiance", "Ses voisins vont bien.", null)
              : new Reading("Ambiance", "Des voisins sont sans emploi, sans maison, malades ou affamés, et ça le touche.",
                "Réglez les problèmes des autres citoyens.");
            case "health" -> new Reading("Santé", "Il est malade.", "Cliquez sur la pastille « Malade » de sa fiche.");
            case "homelessness" -> good
              ? new Reading("Logement", "Sa maison est spacieuse.", null)
              : home == null
                ? new Reading("Logement", "Il n'a pas de maison.", "Construisez ou agrandissez une maison.")
                : new Reading("Logement", "Sa maison (niveau " + homeLevel + ") est trop modeste : il est content à partir du niveau 3.",
                  "Améliorez sa maison.");
            case "unemployment" -> good
              ? new Reading("Travail", "Il travaille dans un bâtiment de niveau 4 ou plus, et il en est fier.", null)
              : new Reading("Travail", "Il n'a pas d'emploi.", "Embauchez-le dans une hutte.");
            case "idleatjob" -> new Reading("Désœuvrement", "Il est à son poste mais ne peut pas travailler.",
              "Voyez ce qui manque dans les demandes de sa hutte.");
            case "slepttonight" -> good
              ? new Reading("Sommeil", "Il dort bien.", null)
              : new Reading("Sommeil", "Il n'a pas dormi depuis deux nuits ou plus.", "Il lui faut une maison avec un lit.");
            case "food" -> good
              ? new Reading("Repas", "Il mange varié et de bonne qualité.", null)
              : new Reading("Repas", "Ses repas manquent de variété ou de qualité. Plus sa maison est grande, plus il est exigeant.",
                "Proposez plus de plats différents au restaurant.");
            case "greatfood" -> new Reading("Bon repas", "Il a mangé un plat de grande qualité il y a peu.", null);
            case "school" -> good
              ? new Reading("École", "Il va à l'école.", null)
              : new Reading("École", "Cet enfant ne va pas à l'école.", "Construisez une école.");
            case "mysticalsite" -> new Reading("Site mystique", "La colonie possède un site mystique.", null);
            case "damage" -> new Reading("Blessure", "Il a été blessé aujourd'hui.", null);
            case "death" -> new Reading("Deuil", "Un habitant est mort ces derniers jours.", null);
            case "raidwithoutdeath" -> new Reading("Raid repoussé", "La colonie a repoussé un raid sans perdre personne.", null);
            case "quest" -> new Reading("Quête", good ? "Une quête lui a fait plaisir." : "Une quête l'a contrarié.", null);
            default -> new Reading(Component.translatable(NAME_KEY + id).getString(), Component.translatable(DESC_KEY + id).getString(), null);
        };
    }
}
