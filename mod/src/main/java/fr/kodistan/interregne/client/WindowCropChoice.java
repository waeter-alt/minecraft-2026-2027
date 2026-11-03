package fr.kodistan.interregne.client;

import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import fr.kodistan.interregne.network.FarmCropPayload;
import fr.kodistan.interregne.network.OpenFarmCropPayload;
import fr.kodistan.interregne.production.Farms.Crop;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Picking a crop, in two places: for a farm, right before its construction or upgrade starts (every crop allowed),
 * and for a scarecrow field (only the crops the colony's farms grow, listed first; the others greyed out with why).
 */
public class WindowCropChoice extends AbstractWindowSkeleton
{
    private static final int ROWS = 4;

    private final List<Crop> order = new ArrayList<>();

    @Nullable
    private final OpenFarmCropPayload farm;

    @Nullable
    private final BOWindow parent;

    @Nullable
    private final Consumer<ItemStack> sow;

    private boolean answered;

    private WindowCropChoice(@Nullable final OpenFarmCropPayload farm, @Nullable final BOWindow parent, @Nullable final Consumer<ItemStack> sow)
    {
        super("interregne:gui/crop_choice.xml");
        this.farm = farm;
        this.parent = parent;
        this.sow = sow;
        for (int row = 0; row < ROWS; row++)
        {
            final int index = row;
            registerButton("crop" + row, () -> choose(index));
        }
        registerButton("cancel", this::cancel);
        if (farm != null)
        {
            showFarm(farm);
        }
        else
        {
            showField();
        }
    }

    /**
     * The farm's crop, asked before its construction or upgrade.
     */
    public static void openForFarm(final OpenFarmCropPayload payload)
    {
        new WindowCropChoice(payload, null, null).open();
    }

    /**
     * A scarecrow field's crop; the choice goes back to the field window.
     */
    public static void openForField(final BOWindow fieldWindow, final Consumer<ItemStack> sow)
    {
        new WindowCropChoice(null, fieldWindow, sow).open();
    }

    private void showFarm(final OpenFarmCropPayload payload)
    {
        findPaneOfTypeByID("title", Text.class).setText(Component.literal(payload.upgrade() ? "Amélioration de la ferme" : "Nouvelle ferme"));
        findPaneOfTypeByID("subtitle", Text.class).setText(Component.literal("Que produira cette ferme ? Une ferme, une culture."));
        final Crop current = Crop.byId(payload.current());
        for (final Crop crop : Crop.values())
        {
            order.add(crop);
        }
        for (int row = 0; row < ROWS; row++)
        {
            final Crop crop = order.get(row);
            fill(row, crop, true, crop == current ? "Culture actuelle" : "");
        }
    }

    private void showField()
    {
        findPaneOfTypeByID("title", Text.class).setText(Component.literal("Semer ce champ"));
        findPaneOfTypeByID("subtitle", Text.class).setText(Component.literal("Le champ renforce les fermes de sa culture."));
        final List<String> grown = ClientFarmCrops.get();
        for (final Crop crop : Crop.values())
        {
            if (grown.contains(crop.id()))
            {
                order.add(crop);
            }
        }
        for (final Crop crop : Crop.values())
        {
            if (!order.contains(crop))
            {
                order.add(crop);
            }
        }
        for (int row = 0; row < ROWS; row++)
        {
            final Crop crop = order.get(row);
            final boolean available = grown.contains(crop.id());
            fill(row, crop, available, available ? "" : "Il faut d'abord une ferme de " + crop.label().toLowerCase());
        }
    }

    private void fill(final int row, final Crop crop, final boolean enabled, final String hint)
    {
        findPaneOfTypeByID("icon" + row, ItemIcon.class).setItem(crop.produceStack());
        final Button button = findPaneOfTypeByID("crop" + row, Button.class);
        button.setText(Component.literal(crop.label()));
        button.setEnabled(enabled);
        final Text text = findPaneOfTypeByID("hint" + row, Text.class);
        text.setText(Component.literal(hint));
        text.setColors(enabled ? 0x2E7D32 : 0xC62828);
    }

    private void choose(final int row)
    {
        final Crop crop = order.get(row);
        answered = true;
        if (farm != null)
        {
            PacketDistributor.sendToServer(new FarmCropPayload(farm.hut(), crop.id()));
            close();
        }
        else if (sow != null && parent != null)
        {
            sow.accept(crop.seedStack());
            parent.open();
        }
    }

    private void cancel()
    {
        if (parent != null)
        {
            parent.open();
        }
        else
        {
            close();
        }
    }

    @Override
    public void onClosed()
    {
        super.onClosed();
        if (farm != null && !answered)
        {
            // Closing without choosing cancels the construction (nothing has been paid yet)
            answered = true;
            PacketDistributor.sendToServer(new FarmCropPayload(farm.hut(), ""));
        }
    }
}
