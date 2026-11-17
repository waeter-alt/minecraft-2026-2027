package fr.kodistan.interregne.routes;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * What a route site lays, cell after cell along a centre line: a new road ({@link RouteBuilder}) or an existing one
 * raised to its next level ({@link RoadDresser}).
 */
public interface RouteWork
{
    int cells();

    /**
     * Centre of cell i, at the road's height.
     */
    BlockPos cell(int i);

    /**
     * Every block to set for cell i, worked out from the world as it is when its turn comes.
     */
    List<RouteStep> slice(int i);
}
