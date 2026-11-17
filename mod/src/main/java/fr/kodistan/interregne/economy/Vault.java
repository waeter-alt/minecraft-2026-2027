package fr.kodistan.interregne.economy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ldtteam.structurize.api.RotationMirror;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The vault of a regional bank: the vault room of its building fills with wealth as the treasury does.
 * Each level's blueprint leaves the room's slots empty and lists them in {@code data/interregne/bank_vault.json}
 * (written by tools/gen_bank_blueprints.py): floor piles, gold bars, open chests, gem clusters, jewel cushions on
 * pedestals. A slot appears once the vault is full enough for it and then grows through six stages, so the vault
 * swells all the way up to a overflowing hoard. Only air and the vault's own decorations are ever replaced.
 */
public final class Vault
{
    private static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "bank_vault.json");

    /**
     * One decoration slot, relative to the hut and in the blueprint's north-facing frame.
     * It shows from fill ratio {@code from}, and reaches its last stage {@code span} of the fill later.
     */
    private record Slot(BlockPos offset, VaultHoardBlock.Kind kind, Direction facing, double from, double span)
    {
    }

    private static Map<Integer, List<Slot>> layouts;

    private Vault()
    {
    }

    /**
     * Lay out the decorations for this fill level in the bank's vault room.
     */
    public static void refresh(final Level level, final BlockPos bank, final long balance, final long capacity)
    {
        final Layout layout = layout(level, bank);
        if (layout == null)
        {
            return;
        }
        if (!Config.VAULT_DECORATIONS.get())
        {
            clear(level, bank);
            return;
        }
        final double ratio = capacity <= 0 ? (balance > 0 ? 1 : 0) : Math.min(1.0, (double) balance / capacity);
        for (final Slot slot : layout.slots)
        {
            final BlockPos pos = bank.offset(layout.rm.applyToPos(slot.offset));
            int stage = 0;
            if (balance > 0 && ratio >= slot.from - 1e-9)
            {
                stage = (int) Math.clamp(Math.ceil((ratio - slot.from) / slot.span * VaultHoardBlock.MAX_STAGE), 1, VaultHoardBlock.MAX_STAGE);
            }
            place(level, pos, stage == 0 ? null : layout.rm.applyToBlockState(state(slot, stage)));
        }
    }

    /**
     * Remove every decoration of this bank's vault, e.g. when it is broken or its decorations are switched off.
     */
    public static void clear(final Level level, final BlockPos bank)
    {
        final Layout layout = layout(level, bank);
        if (layout == null)
        {
            return;
        }
        for (final Slot slot : layout.slots)
        {
            place(level, bank.offset(layout.rm.applyToPos(slot.offset)), null);
        }
    }

    private static BlockState state(final Slot slot, final int stage)
    {
        return RegionalBanks.HOARD.get().defaultBlockState().setValue(VaultHoardBlock.KIND, slot.kind).setValue(VaultHoardBlock.STAGE, stage)
                 .setValue(VaultHoardBlock.FACING, slot.facing);
    }

    private record Layout(List<Slot> slots, RotationMirror rm)
    {
    }

    private static Layout layout(final Level level, final BlockPos bank)
    {
        if (level.getServer() == null)
        {
            return null;
        }
        final IColony colony = IColonyManager.getInstance().getIColony(level, bank);
        final BuildingBanqueRegionale building = colony == null ? null : RegionalBank.of(colony);
        if (building == null || !bank.equals(building.getPosition()))
        {
            return null;
        }
        final List<Slot> slots = layouts(level.getServer()).get(building.getBuildingLevel());
        if (slots == null)
        {
            return null;
        }
        final var entity = building.getTileEntity();
        return new Layout(slots, entity == null ? RotationMirror.NONE : entity.getRotationMirror());
    }

    /**
     * A slot is set (or emptied, for a null state) only where there is air or one of our own decorations.
     */
    private static void place(final Level level, final BlockPos pos, final BlockState wanted)
    {
        final BlockState current = level.getBlockState(pos);
        final boolean ours = current.is(RegionalBanks.HOARD.get());
        if (wanted == null)
        {
            if (ours)
            {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        else if ((ours || current.isAir()) && !current.equals(wanted))
        {
            level.setBlock(pos, wanted, Block.UPDATE_ALL);
        }
    }

    private static Map<Integer, List<Slot>> layouts(final MinecraftServer server)
    {
        if (layouts == null)
        {
            final Map<Integer, List<Slot>> loaded = new HashMap<>();
            try (Reader reader = new InputStreamReader(server.getResourceManager().getResourceOrThrow(FILE).open()))
            {
                final JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                for (final Map.Entry<String, JsonElement> level : root.entrySet())
                {
                    final List<Slot> slots = new ArrayList<>();
                    for (final JsonElement element : level.getValue().getAsJsonArray())
                    {
                        final JsonObject o = element.getAsJsonObject();
                        final var pos = o.getAsJsonArray("pos");
                        slots.add(new Slot(new BlockPos(pos.get(0).getAsInt(), pos.get(1).getAsInt(), pos.get(2).getAsInt()),
                          VaultHoardBlock.Kind.valueOf(o.get("kind").getAsString().toUpperCase()),
                          Direction.byName(o.get("facing").getAsString()), o.get("from").getAsDouble(), o.get("span").getAsDouble()));
                    }
                    loaded.put(Integer.parseInt(level.getKey()), slots);
                }
            }
            catch (final Exception e)
            {
                InterregneMod.LOGGER.warn("Banque régionale: vault layout unreadable, no hoard: {}", e.toString());
            }
            layouts = loaded;
        }
        return layouts;
    }
}
