package fr.kodistan.interregne.war;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Which huts are currently in ruins from a past assault, excluded from future targeting until repaired — and which
 * colonies have a wall breach still open. Unlike the rest of the war slice (troops, active assaults — memory-only,
 * lost on reload), this has to survive a server restart: the ruins and the breach are real, saved state (blocks in
 * the world, missing steps against the wall's own trace), so a fresh server forgetting "already damaged" would
 * hand that building right back out as a target, or hide the wall's "Tout reconstruire" entry entirely.
 */
final class WarRuinsData extends SavedData
{
    private static final String NAME = "interregne_war_ruins";

    private static final SavedData.Factory<WarRuinsData> FACTORY = new SavedData.Factory<>(WarRuinsData::new, (tag, provider) ->
    {
        final WarRuinsData data = new WarRuinsData();
        final ListTag list = tag.getList("huts", Tag.TAG_LONG);
        for (int i = 0; i < list.size(); i++)
        {
            data.ruined.add(BlockPos.of(((LongTag) list.get(i)).getAsLong()));
        }
        final ListTag walls = tag.getList("walls", Tag.TAG_STRING);
        for (int i = 0; i < walls.size(); i++)
        {
            data.damagedWalls.add(walls.getString(i));
        }
        return data;
    });

    private final Set<BlockPos> ruined = new HashSet<>();
    private final Set<String> damagedWalls = new HashSet<>();

    static WarRuinsData get(final ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    boolean isRuined(final BlockPos hut)
    {
        return ruined.contains(hut);
    }

    void markRuined(final BlockPos hut)
    {
        ruined.add(hut);
        setDirty();
    }

    void clearRuined(final BlockPos hut)
    {
        ruined.remove(hut);
        setDirty();
    }

    boolean isWallDamaged(final String colonyKey)
    {
        return damagedWalls.contains(colonyKey);
    }

    void markWallDamaged(final String colonyKey)
    {
        if (damagedWalls.add(colonyKey))
        {
            setDirty();
        }
    }

    void clearWallDamaged(final String colonyKey)
    {
        if (damagedWalls.remove(colonyKey))
        {
            setDirty();
        }
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull final CompoundTag tag, @NotNull final HolderLookup.Provider provider)
    {
        final ListTag list = new ListTag();
        for (final BlockPos pos : ruined)
        {
            list.add(LongTag.valueOf(pos.asLong()));
        }
        tag.put("huts", list);
        final ListTag walls = new ListTag();
        for (final String key : damagedWalls)
        {
            walls.add(StringTag.valueOf(key));
        }
        tag.put("walls", walls);
        return tag;
    }
}
