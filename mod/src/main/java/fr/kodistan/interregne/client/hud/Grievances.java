package fr.kodistan.interregne.client.hud;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.interactionhandling.IInteractionResponseHandler;
import com.minecolonies.core.colony.interactionhandling.RequestBasedInteraction;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The grievance panel: every citizen of the player's colony with an unmet request or a problem, what is wrong and how to fix it.
 * Shown only while something is unresolved; the citizens concerned glow through walls (see MinecraftMixin).
 */
@EventBusSubscriber(modid = InterregneMod.MODID, value = Dist.CLIENT)
public final class Grievances
{
    /**
     * Outline color of the citizens waiting on the player.
     */
    public static final int GLOW_COLOR = 0xFFB12E;

    private static final String NO_HOSPITAL = "com.minecolonies.coremod.ai.nohospital";
    private static final int REFRESH_TICKS = 10;
    private static final int MAX_SHOWN = 5;
    private static final float SCALE = 0.6F;
    private static final int WIDTH = 210;
    private static final int PAD = 4;

    private static final int FILL  = 0xD8180F08;
    private static final int EDGE  = 0xFF6E4A22;
    private static final int TITLE = 0xFFFFC857;
    private static final int NAME  = 0xFFFFE3A3;
    private static final int TEXT  = 0xFFE8E0D0;
    private static final int URGENT = 0xFFFF6A50;

    private record Grievance(String citizen, Component text, boolean blocking)
    {
    }

    private static List<Grievance> current = List.of();
    private static Set<Integer> glowing = Set.of();
    private static int ticks;

    private Grievances()
    {
    }

    public static boolean glows(final int entityId)
    {
        return glowing.contains(entityId);
    }

    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Post event)
    {
        if (++ticks % REFRESH_TICKS != 0)
        {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        final IColonyView colony = minecraft.player == null || minecraft.level == null ? null : colonyOf(minecraft);
        if (colony == null)
        {
            current = List.of();
            glowing = Set.of();
            return;
        }
        final List<Grievance> found = new ArrayList<>();
        final Set<Integer> ids = new HashSet<>();
        for (final ICitizenDataView citizen : colony.getCitizens().values())
        {
            for (final IInteractionResponseHandler interaction : citizen.getOrderedInteractions())
            {
                if (!unresolved(interaction, minecraft))
                {
                    continue;
                }
                found.add(new Grievance(citizen.getName(), resolution(citizen, interaction),
                  interaction.getPriority().getPriority() >= ChatPriority.BLOCKING.getPriority()));
                ids.add(citizen.getEntityId());
            }
        }
        found.sort(Comparator.comparing((Grievance g) -> !g.blocking()));
        current = found;
        glowing = ids;
    }

    private static IColonyView colonyOf(final Minecraft minecraft)
    {
        final IColony owned = IColonyManager.getInstance().getIColonyByOwner(minecraft.level, minecraft.player);
        if (owned instanceof IColonyView view)
        {
            return view;
        }
        final IColonyView near = IColonyManager.getInstance().getClosestColonyView(minecraft.level, minecraft.player.blockPosition());
        return near != null && near.getPermissions().hasPermission(minecraft.player, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS) ? near : null;
    }

    /**
     * How many grievances this citizen has right now, for the hut window.
     */
    public static int count(final ICitizenDataView citizen)
    {
        final Minecraft minecraft = Minecraft.getInstance();
        int count = 0;
        for (final IInteractionResponseHandler interaction : citizen.getOrderedInteractions())
        {
            if (unresolved(interaction, minecraft))
            {
                count++;
            }
        }
        return count;
    }

    /**
     * A request, a sickness, a complaint: anything the citizen waits on, not small talk.
     */
    private static boolean unresolved(final IInteractionResponseHandler interaction, final Minecraft minecraft)
    {
        try
        {
            return interaction.getPriority().getPriority() >= ChatPriority.PENDING.getPriority() && interaction.isVisible(minecraft.level);
        }
        catch (final RuntimeException e)
        {
            return false;
        }
    }

    /**
     * What is wrong and how to fix it, in a sentence.
     */
    private static Component resolution(final ICitizenDataView citizen, final IInteractionResponseHandler interaction)
    {
        final Component inquiry = interaction.getInquiry();
        if (inquiry.getContents() instanceof TranslatableContents translatable && NO_HOSPITAL.equals(translatable.getKey())
              && translatable.getArgs().length >= 2)
        {
            return Component.empty()
              .append(arg(translatable.getArgs()[0]))
              .append(" : un hôpital, ou donnez-lui ")
              .append(arg(translatable.getArgs()[1]))
              .append(".");
        }
        if (interaction instanceof RequestBasedInteraction)
        {
            return Component.empty().append(inquiry).append(" À lui apporter, ou à ranger à l'entrepôt.");
        }
        return inquiry;
    }

    private static Component arg(final Object value)
    {
        return value instanceof Component component ? component : Component.literal(String.valueOf(value));
    }

    /**
     * Draw the panel under the wallet, right-aligned; nothing at all when every citizen is satisfied.
     */
    static void render(final GuiGraphics graphics, final Font font, final int right, final int top)
    {
        final List<Grievance> list = current;
        if (list.isEmpty())
        {
            return;
        }
        // Drawn small: a reminder in the corner, not a window
        graphics.pose().pushPose();
        graphics.pose().translate(right, top, 0);
        graphics.pose().scale(SCALE, SCALE, 1);
        final int left = -WIDTH;
        final int textWidth = WIDTH - 2 * PAD;
        final List<FormattedCharSequence> lines = new ArrayList<>();
        final List<Integer> colors = new ArrayList<>();
        lines.add(Component.literal("Doléances (" + list.size() + ")").getVisualOrderText());
        colors.add(TITLE);
        for (int i = 0; i < Math.min(MAX_SHOWN, list.size()); i++)
        {
            final Grievance grievance = list.get(i);
            lines.add(Component.literal((grievance.blocking() ? "⚠ " : "• ") + grievance.citizen()).getVisualOrderText());
            colors.add(grievance.blocking() ? URGENT : NAME);
            for (final FormattedCharSequence line : font.split(grievance.text(), textWidth))
            {
                lines.add(line);
                colors.add(TEXT);
            }
        }
        if (list.size() > MAX_SHOWN)
        {
            lines.add(Component.literal("… et " + (list.size() - MAX_SHOWN) + " autres").getVisualOrderText());
            colors.add(NAME);
        }
        final int height = PAD * 2 + lines.size() * (font.lineHeight + 1);
        graphics.fill(left, 0, 0, height, FILL);
        graphics.renderOutline(left, 0, WIDTH, height, EDGE);
        int y = PAD;
        for (int i = 0; i < lines.size(); i++)
        {
            graphics.drawString(font, lines.get(i), left + PAD, y, colors.get(i), true);
            y += font.lineHeight + 1;
        }
        graphics.pose().popPose();
    }
}
