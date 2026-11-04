package fr.kodistan.interregne.client.hut;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.core.client.gui.AbstractWindowRequestTree;
import com.minecolonies.core.network.messages.server.colony.UpdateRequestStateMessage;
import fr.kodistan.interregne.client.catalogue.CatalogueIndex;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.ParchmentScreen;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One request, in full: what is asked (every item that would do), for whom and where, who is on it, and what can be
 * done about it here: give it from the inventory, or cancel it. Opens from our citizen, hut, hub and clipboard screens,
 * and in place of MineColonies' request detail window.
 */
public final class RequestScreen extends ParchmentScreen
{
    private static final int WIDTH = 300;
    private static final int HEIGHT = 200;

    private record Hit(int x, int y, int width, int height, Runnable action)
    {
    }

    private final IRequest<?> request;
    private final IColonyView colony;
    /**
     * The citizen to hand the items to, when opened from their sheet.
     */
    @Nullable
    private final ICitizenDataView citizen;
    /**
     * MineColonies' window this replaces, which knows whether the request can be given or cancelled from there.
     */
    @Nullable
    private final BOWindow previous;
    private final List<Hit> hits = new ArrayList<>();
    private final List<Ui.Slot> slots = new ArrayList<>();
    @Nullable
    private String hint;

    private RequestScreen(final IRequest<?> request, final IColonyView colony, @Nullable final ICitizenDataView citizen, @Nullable final Screen parent,
      @Nullable final BOWindow previous)
    {
        super(Component.literal("Demande"), parent);
        this.request = request;
        this.colony = colony;
        this.citizen = citizen;
        this.previous = previous;
    }

    /**
     * From one of our screens; Escape comes back to it.
     */
    public static void open(final IRequest<?> request, final IColonyView colony, @Nullable final ICitizenDataView citizen, final Screen parent)
    {
        Minecraft.getInstance().setScreen(new RequestScreen(request, colony, citizen, parent, null));
    }

    /**
     * In place of MineColonies' request detail window; Escape reopens the window it came from.
     */
    public static void openFrom(final IRequest<?> request, final IColonyView colony, @Nullable final BOWindow previous)
    {
        Minecraft.getInstance().setScreen(new RequestScreen(request, colony, null, null, previous));
    }

    @Override
    protected void init()
    {
        super.init();
        w = Math.min(width - 16, WIDTH);
        h = Math.min(height - 16, HEIGHT);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
    }

    @Override
    @Nullable
    protected String crumb()
    {
        return parent instanceof ClipboardScreen ? "Presse-papiers" : citizen != null ? citizen.getName() : null;
    }

    @Override
    public void onClose()
    {
        if (parent == null && previous != null)
        {
            previous.open();
            return;
        }
        super.onClose();
    }

    // --- What can be done ---

    private boolean canGive()
    {
        if (citizen != null)
        {
            return minecraft.player.isCreative() || CitizenBits.carried(request) > 0;
        }
        return previous instanceof AbstractWindowRequestTree tree && tree.fulfillable(request);
    }

    private void give()
    {
        if (citizen != null)
        {
            if (!CitizenBits.give(colony, citizen, request))
            {
                minecraft.player.displayClientMessage(Component.literal("Ce que tu portes en armure ou en main gauche ne peut pas être donné."), true);
                return;
            }
        }
        else if (previous instanceof AbstractWindowRequestTree tree)
        {
            tree.fulfill(request);
        }
        onClose();
    }

    /**
     * Only a request someone asked for directly can be cancelled, not one a worker opened to answer another.
     */
    private boolean canCancel()
    {
        if (previous instanceof AbstractWindowRequestTree tree)
        {
            return tree.cancellable(request);
        }
        return !request.hasParent();
    }

    private void cancel()
    {
        final IBuildingView building = requesterBuilding();
        if (building != null)
        {
            building.onRequestedRequestCancelled(colony.getRequestManager(), request);
        }
        new UpdateRequestStateMessage(colony, request.getId(), RequestState.CANCELLED, null).sendToServer();
        onClose();
    }

    @Nullable
    private IBuildingView requesterBuilding()
    {
        try
        {
            return colony.getBuilding(request.getRequester().getLocation().getInDimensionLocation());
        }
        catch (final RuntimeException e)
        {
            return null;
        }
    }

    // --- Drawing ---

    /**
     * The item shown large: every item that would do, in turn.
     */
    static ItemStack shown(final IRequest<?> request)
    {
        final List<ItemStack> stacks = request.getDisplayStacks();
        if (stacks.isEmpty())
        {
            return new ItemStack(Items.PAPER);
        }
        return stacks.get((int) (Util.getMillis() / 1500 % stacks.size()));
    }

    @Override
    protected void renderContent(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        hits.clear();
        slots.clear();
        hint = null;
        final int x = x0 + 10;
        final int width = w - 20;
        int y = y0 + HEADER + 8;

        // What is asked, large, with the long text beside it
        final ItemStack stack = shown(request);
        Ui.plate(graphics, x, y, x + 36, y + 36, Palette.PAPER_LIGHT, Palette.RULE, Palette.PAPER_LIGHT);
        Icon.of(stack).draw(graphics, x + 2, y + 2, 32);
        slots.add(new Ui.Slot(x + 2, y + 2, 32, stack));
        final int textX = x + 44;
        final int textW = width - 44;
        graphics.drawString(font, Component.literal(fit(request.getShortDisplayString().getString(), textW)).withStyle(ChatFormatting.BOLD), textX, y + 2,
          Palette.INK, false);
        final String state = CitizenBits.requestStateText(request);
        Ui.chip(graphics, font, textX, y + 13, state, CitizenBits.requestState(request));
        final String longText = request.getLongDisplayString().getString().replace("§f", "");
        if (!longText.equals(request.getShortDisplayString().getString()))
        {
            Ui.wrapped(graphics, font, Component.literal(longText), textX, y + 27, textW, Palette.MUTED, 9);
        }
        y += 42;

        // Every item that would do
        final List<ItemStack> stacks = request.getDisplayStacks();
        if (stacks.size() > 1)
        {
            Ui.label(graphics, font, "N'IMPORTE LEQUEL DE CEUX-CI", x, y);
            y += 11;
            final int perRow = width / 18;
            final int shownCount = Math.min(stacks.size(), perRow * 2);
            for (int i = 0; i < shownCount; i++)
            {
                final int sx = x + (i % perRow) * 18;
                final int sy = y + (i / perRow) * 18;
                graphics.fill(sx, sy, sx + 17, sy + 17, Palette.PAPER_DARK);
                Icon.of(stacks.get(i)).draw(graphics, sx, sy, 16);
                slots.add(new Ui.Slot(sx, sy, 16, stacks.get(i)));
            }
            y += ((shownCount + perRow - 1) / perRow) * 18 + 4;
        }

        // For whom, where, who is on it
        y = Ui.separator(graphics, x, y - 4, width);
        final IBuildingView building = requesterBuilding();
        String requester;
        try
        {
            requester = request.getRequester().getRequesterDisplayName(colony.getRequestManager(), request).getString();
        }
        catch (final RuntimeException e)
        {
            requester = "?";
        }
        y = infoLine(graphics, mouseX, mouseY, x, y, width, "Pour", requester, null);
        if (building != null)
        {
            final BlockPos pos = building.getPosition();
            final int distance = (int) Math.sqrt(minecraft.player.blockPosition().distSqr(pos));
            final String place = nameOf(building) + " · à " + distance + (distance > 1 ? " blocs" : " bloc");
            final Screen back = this;
            y = infoLine(graphics, mouseX, mouseY, x, y, width, "Où", place, () -> HutScreen.openWith(building, back));
        }
        final String resolver = resolverName();
        if (resolver != null)
        {
            y = infoLine(graphics, mouseX, mouseY, x, y, width, "Qui s'en charge", resolver, null);
        }
        if (canGiveHere())
        {
            final int carried = CitizenBits.carried(request);
            y = infoLine(graphics, mouseX, mouseY, x, y, width, "Sur toi", minecraft.player.isCreative() ? "créatif : tout" : carried > 0 ? carried + " qui conviennent"
                                                                                                       : "rien qui convienne", null);
        }

        // Actions: give in green, cancel in red, each only when it can be done here
        final int buttonY = y0 + h - 8 - Ui.BUTTON_H;
        int buttonX = x0 + w - 10;
        if (canGiveHere())
        {
            final int giveW = 90;
            buttonX -= giveW;
            final boolean enabled = canGive();
            final boolean over = Ui.inside(mouseX, mouseY, buttonX, buttonY, giveW, Ui.BUTTON_H);
            Ui.button(graphics, font, buttonX, buttonY, giveW, Ui.BUTTON_H, "Donner", Ui.ButtonStyle.PRIMARY, enabled, over);
            if (enabled)
            {
                hits.add(new Hit(buttonX, buttonY, giveW, Ui.BUTTON_H, this::give));
            }
            else if (over)
            {
                hint = "Tu n'as pas cet objet sur toi.";
            }
            buttonX -= 6;
        }
        if (canCancel())
        {
            final String label = "Annuler la demande";
            final int cancelW = font.width(label) + 16;
            buttonX -= cancelW;
            final boolean over = Ui.inside(mouseX, mouseY, buttonX, buttonY, cancelW, Ui.BUTTON_H);
            Ui.button(graphics, font, buttonX, buttonY, cancelW, Ui.BUTTON_H, label, Ui.ButtonStyle.DANGER, true, over);
            hits.add(new Hit(buttonX, buttonY, cancelW, Ui.BUTTON_H, this::cancel));
            if (over)
            {
                hint = "Le bâtiment ne l'attendra plus. Il la redemandera s'il en a encore besoin.";
            }
        }

        for (final Ui.Slot slot : slots)
        {
            if (Ui.inside(mouseX, mouseY, slot.x(), slot.y(), slot.size(), slot.size()))
            {
                graphics.renderTooltip(font, slot.stack(), mouseX, mouseY);
            }
        }
        if (hint != null)
        {
            graphics.renderTooltip(font, font.split(Component.literal(hint), 200), mouseX, mouseY);
        }
    }

    /**
     * Whether giving makes sense from here: from a citizen's sheet, or from a MineColonies window that can give.
     */
    private boolean canGiveHere()
    {
        return citizen != null || previous instanceof AbstractWindowRequestTree tree && tree.canFulFill();
    }

    /**
     * A label in the margin and its value; a value with an action is a link.
     */
    private int infoLine(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width, final String label,
      final String value, @Nullable final Runnable action)
    {
        graphics.drawString(font, label, x, y, Palette.MUTED, false);
        final int valueX = x + 90;
        final String text = fit(value, width - 90 - (action == null ? 0 : 10));
        final boolean over = action != null && Ui.inside(mouseX, mouseY, valueX - 2, y - 2, font.width(text) + 14, 12);
        graphics.drawString(font, Component.literal(text).withStyle(over ? ChatFormatting.UNDERLINE : ChatFormatting.RESET), valueX, y, Palette.INK, false);
        if (action != null)
        {
            graphics.drawString(font, "›", valueX + font.width(text) + 4, y, over ? Palette.SELECTED : Palette.MUTED, false);
            hits.add(new Hit(valueX - 2, y - 2, font.width(text) + 14, 12, action));
        }
        return y + 12;
    }

    @Nullable
    private String resolverName()
    {
        try
        {
            final IRequestResolver<?> resolver = colony.getRequestManager().getResolverForRequest(request.getId());
            return resolver == null ? null : resolver.getRequesterDisplayName(colony.getRequestManager(), request).getString();
        }
        catch (final RuntimeException e)
        {
            return null;
        }
    }

    static String nameOf(final IBuildingView building)
    {
        final String custom = building.getCustomName();
        if (custom != null && !custom.isEmpty())
        {
            return custom;
        }
        return CatalogueIndex.buildingName(building.getBuildingType().getRegistryName().getPath(),
          Component.translatable(building.getBuildingDisplayName()).getString());
    }

    @Override
    protected boolean clicked(final double mouseX, final double mouseY)
    {
        for (final Hit hit : List.copyOf(hits))
        {
            if (Ui.inside(mouseX, mouseY, hit.x(), hit.y(), hit.width(), hit.height()))
            {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                hit.action().run();
                return true;
            }
        }
        return false;
    }
}
