package fr.kodistan.interregne.dev.gametest;

import com.minecolonies.api.inventory.InventoryCitizen;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * A citizen only carries items from allowed mods. The test narrows the allowlist to this mod alone, so that vanilla
 * items stand in for the "foreign" modded gear that froze the 2025 server.
 */
@GameTestHolder(InterregneMod.MODID)
@PrefixGameTestTemplate(false)
public final class CitizenItemTests
{
    private CitizenItemTests()
    {
    }

    @GameTest(template = "empty")
    public static void foreignItemsRefused(final GameTestHelper helper)
    {
        final List<? extends String> before = Config.CITIZEN_ITEM_NAMESPACES.get();
        Config.CITIZEN_ITEM_NAMESPACES.set(List.of("interregne"));
        try
        {
            final InventoryCitizen inventory = new InventoryCitizen("test", true);
            final ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);

            final ItemStack rest = inventory.insertItem(0, sword, false);
            if (rest.getCount() != 1 || !inventory.getStackInSlot(0).isEmpty())
            {
                helper.fail("a foreign item got into the citizen");
            }
            if (inventory.isItemValid(0, sword))
            {
                helper.fail("a foreign item was reported valid");
            }
            inventory.forceArmorStackToSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            if (!inventory.getArmorInSlot(EquipmentSlot.HEAD).isEmpty())
            {
                helper.fail("a foreign helmet was worn");
            }
        }
        finally
        {
            Config.CITIZEN_ITEM_NAMESPACES.set(before);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allowedItemsAccepted(final GameTestHelper helper)
    {
        final InventoryCitizen inventory = new InventoryCitizen("test", true);
        final ItemStack rest = inventory.insertItem(0, new ItemStack(Items.BREAD, 3), false);
        if (!rest.isEmpty() || inventory.getStackInSlot(0).getCount() != 3)
        {
            helper.fail("a vanilla item was refused");
        }
        helper.succeed();
    }
}
