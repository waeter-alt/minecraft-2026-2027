package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import fr.kodistan.interregne.InterregneMod;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.client.gui.WindowInteraction;
import com.minecolonies.core.network.messages.server.colony.UpdateRequestStateMessage;
import com.minecolonies.core.network.messages.server.colony.citizen.TransferItemsToCitizenRequestMessage;
import fr.kodistan.interregne.client.hud.Grievances;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * What the hut window and the citizen window show the same way about a citizen: face, activity, problem chips, and
 * handing over what they ask for.
 */
public final class CitizenBits
{
    /**
     * A chip: its text, colours, and what a click does (null: not clickable).
     */
    public record Chip(String text, int soft, int ink, @Nullable Runnable action)
    {
    }

    @Nullable
    private static Map<String, JsonArray> jobNames;

    private CitizenBits()
    {
    }

    /**
     * Our French name for a job (assets/interregne/job_names.json), in the citizen's gender; MineColonies' own
     * translation for a job we do not know. Takes the job's translation key.
     */
    public static String jobName(final String jobKey, final boolean female)
    {
        final JsonArray names = jobNames().get(jobKey.substring(jobKey.lastIndexOf('.') + 1));
        if (names != null && names.size() == 2)
        {
            return names.get(female ? 1 : 0).getAsString();
        }
        return Component.translatable(jobKey).getString();
    }

    private static synchronized Map<String, JsonArray> jobNames()
    {
        if (jobNames == null)
        {
            jobNames = new HashMap<>();
            try (Reader reader = new InputStreamReader(CitizenBits.class.getResourceAsStream("/assets/" + InterregneMod.MODID + "/job_names.json"), StandardCharsets.UTF_8))
            {
                JsonParser.parseReader(reader).getAsJsonObject().entrySet().stream().filter(e -> e.getValue().isJsonArray())
                  .forEach(e -> jobNames.put(e.getKey(), e.getValue().getAsJsonArray()));
            }
            catch (final Exception e)
            {
                InterregneMod.LOGGER.warn("L'Interregne: job names unreadable", e);
            }
        }
        return jobNames;
    }

    /**
     * The citizen's entity when it is loaded nearby. By entity id first; a citizen just hired from the tavern can carry
     * a stale one, so then by citizen id in the colony.
     */
    @Nullable
    public static LivingEntity entity(final ICitizenDataView citizen)
    {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null)
        {
            return null;
        }
        final Entity byId = minecraft.level.getEntity(citizen.getEntityId());
        if (byId instanceof AbstractEntityCitizen found && found.getCivilianID() == citizen.getId() && found.getCitizenColonyHandler().getColonyId() == citizen.getColonyId())
        {
            return found;
        }
        for (final Entity entity : minecraft.level.entitiesForRendering())
        {
            if (entity instanceof AbstractEntityCitizen found && found.getCivilianID() == citizen.getId() && found.getCitizenColonyHandler().getColonyId() == citizen.getColonyId())
            {
                return found;
            }
        }
        return null;
    }

    /**
     * Draw an entity in our screens without the name floating over its head.
     */
    private static void withoutName(final LivingEntity living, final Runnable draw)
    {
        final boolean shown = living.isCustomNameVisible();
        living.setCustomNameVisible(false);
        try
        {
            draw.run();
        }
        finally
        {
            living.setCustomNameVisible(shown);
        }
    }

    /**
     * The citizen's head in a size × size box: the model itself, drawn large and cropped, so it works whatever the skin
     * layout. A plain head when the citizen is not loaded nearby.
     */
    public static void face(final GuiGraphics graphics, final ICitizenDataView citizen, final int x, final int y, final int size)
    {
        final LivingEntity living = entity(citizen);
        if (living != null)
        {
            graphics.fill(x, y, x + size, y + size, Palette.PAPER_DARK);
            // Box centre at head height; the "mouse" at the centre makes the head look straight ahead
            withoutName(living, () -> InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x, y, x + size, y + size, size * 26 / 16,
              living.getBbHeight() * 0.38F, x + size / 2F, y + size / 2F, living));
            return;
        }
        Icon.of(Items.PLAYER_HEAD).draw(graphics, x, y, size);
    }

    /**
     * The whole citizen, standing in the box and turning towards the mouse.
     */
    public static void body(final GuiGraphics graphics, final ICitizenDataView citizen, final int x1, final int y1, final int x2, final int y2,
      final int mouseX, final int mouseY)
    {
        final LivingEntity living = entity(citizen);
        if (living != null)
        {
            final int scale = (int) ((y2 - y1) * 0.8F / Math.max(0.5F, living.getBbHeight()));
            withoutName(living, () -> InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x1, y1, x2, y2, scale, 0.0625F, mouseX, mouseY, living));
            return;
        }
        final int size = Math.min(x2 - x1, y2 - y1) / 2;
        Icon.of(Items.PLAYER_HEAD).draw(graphics, (x1 + x2 - size) / 2, (y1 + y2 - size) / 2, size);
    }

    /**
     * What the citizen is doing, as the end of a sentence (« dort »).
     */
    public static String activity(final ICitizenDataView citizen)
    {
        final VisibleCitizenStatus status = citizen.getVisibleStatus();
        if (status == null || status == VisibleCitizenStatus.WORKING)
        {
            return citizen.getJob() == null || citizen.getJob().isEmpty() ? "se promène" : "travaille";
        }
        if (status == VisibleCitizenStatus.SLEEP)
        {
            return "dort";
        }
        if (status == VisibleCitizenStatus.EAT)
        {
            return "mange";
        }
        if (status == VisibleCitizenStatus.SICK)
        {
            return "est malade";
        }
        if (status == VisibleCitizenStatus.HOUSE)
        {
            return "est chez lui";
        }
        if (status == VisibleCitizenStatus.RAIDED)
        {
            return "se cache d'un raid";
        }
        if (status == VisibleCitizenStatus.MOURNING)
        {
            return "est en deuil";
        }
        if (status == VisibleCitizenStatus.BAD_WEATHER)
        {
            return "s'abrite de la pluie";
        }
        return Component.translatable(status.getTranslationKey()).getString().toLowerCase(Locale.FRANCE);
    }

    /**
     * The citizen's problems and mood as chips, each kind in its own colour. Sickness and grievances open the citizen's
     * dialogue, where they are settled.
     */
    public static List<Chip> chips(final ICitizenDataView citizen)
    {
        final List<Chip> chips = new ArrayList<>();
        final Runnable settle = () -> new WindowInteraction(citizen).open();
        if (citizen.isSick())
        {
            chips.add(new Chip("Malade", Palette.SICK_SOFT, Palette.SICK_TEXT, settle));
        }
        // Sickness is itself a grievance: count only the others
        final int others = Grievances.count(citizen) - (citizen.isSick() ? 1 : 0);
        if (others > 0)
        {
            chips.add(new Chip(others == 1 ? "1 doléance" : others + " doléances", Palette.AMBER_SOFT, Palette.AMBER_TEXT, settle));
        }
        final double happiness = citizen.getHappiness();
        if (happiness < 4)
        {
            chips.add(new Chip("Malheureux", Palette.RED_SOFT, Palette.RED, null));
        }
        else if (happiness >= 7)
        {
            chips.add(new Chip("Content", Palette.GREEN_SOFT, Palette.GREEN, null));
        }
        return chips;
    }

    /**
     * A request's state as a chip text and state.
     */
    public static Ui.State requestState(final IRequest<?> request)
    {
        return switch (request.getState())
        {
            case ASSIGNED, IN_PROGRESS, RESOLVED, FOLLOWUP_IN_PROGRESS -> Ui.State.OK;
            default -> Ui.State.WAIT;
        };
    }

    public static String requestStateText(final IRequest<?> request)
    {
        return switch (request.getState())
        {
            case ASSIGNED, IN_PROGRESS -> "Pris en charge";
            case RESOLVED, FOLLOWUP_IN_PROGRESS -> "En livraison";
            default -> "Personne ne l'a";
        };
    }

    /**
     * How many of what the request asks for the player carries outside armour and offhand; 0 when it asks for no item.
     */
    public static int carried(final IRequest<?> request)
    {
        if (!(request.getRequest() instanceof IDeliverable deliverable))
        {
            return 0;
        }
        final Inventory inventory = Minecraft.getInstance().player.getInventory();
        final Predicate<ItemStack> matches = deliverable::matches;
        return InventoryUtils.getItemCountInItemHandler(new InvWrapper(inventory), matches);
    }

    /**
     * Hand the citizen what the request asks for, from the player's inventory (MineColonies' own « fulfill »). Returns
     * false when the player has nothing that fits outside their armour slots.
     */
    @SuppressWarnings("unchecked")
    public static boolean give(final IColonyView colony, final ICitizenDataView citizen, final IRequest<?> request)
    {
        if (!(request.getRequest() instanceof IDeliverable))
        {
            return false;
        }
        final IRequest<? extends IDeliverable> deliverable = (IRequest<? extends IDeliverable>) request;
        final Inventory inventory = Minecraft.getInstance().player.getInventory();
        final boolean creative = Minecraft.getInstance().player.isCreative();
        final Predicate<ItemStack> matches = stack -> deliverable.getRequest().matches(stack);
        final int amount = deliverable.getRequest().getCount();
        final int count = InventoryUtils.getItemCountInItemHandler(new InvWrapper(inventory), matches);
        final ItemStack stack;
        if (creative)
        {
            stack = request.getDisplayStacks().stream().findFirst().orElse(ItemStack.EMPTY);
        }
        else
        {
            if (count <= 0)
            {
                return false;
            }
            // 4 armour slots and the offhand are left alone
            final int usable = inventory.getContainerSize() - 5;
            int slot = -1;
            for (final int candidate : InventoryUtils.findAllSlotsInItemHandlerWith(new InvWrapper(inventory), matches))
            {
                if (candidate < usable)
                {
                    slot = candidate;
                    break;
                }
            }
            if (slot == -1)
            {
                return false;
            }
            stack = inventory.getItem(slot);
        }
        final int given = creative ? amount : Math.min(amount, count);
        if (citizen.getWorkBuilding() != null && colony.getBuilding(citizen.getWorkBuilding()) != null)
        {
            colony.getBuilding(citizen.getWorkBuilding()).onRequestedRequestComplete(colony.getRequestManager(), request);
        }
        new TransferItemsToCitizenRequestMessage(colony, citizen, stack, given).sendToServer();
        final ItemStack copy = stack.copy();
        copy.setCount(given);
        new UpdateRequestStateMessage(colony, request.getId(), RequestState.OVERRULED, copy).sendToServer();
        return true;
    }
}
