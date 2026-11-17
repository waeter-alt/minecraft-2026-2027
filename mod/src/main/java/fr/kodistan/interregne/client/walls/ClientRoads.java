package fr.kodistan.interregne.client.walls;

import fr.kodistan.interregne.network.RoadLinesPayload;
import fr.kodistan.interregne.walls.WallRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The trade roads' centre lines as the server last told this client: for each cell, the colonies the road links.
 */
public final class ClientRoads
{
    /** Cell (WallRegistry.cell) to {colonyA, colonyB}. */
    private static Map<Long, int[]> cells = Map.of();
    private static int version;

    private ClientRoads()
    {
    }

    public static void put(final RoadLinesPayload payload)
    {
        final Map<Long, int[]> next = new HashMap<>();
        final List<Integer> data = payload.data();
        int i = 0;
        while (i + 2 < data.size())
        {
            final int[] ends = {data.get(i), data.get(i + 1)};
            final int n = data.get(i + 2);
            i += 3;
            for (int k = 0; k < n && i + 1 < data.size(); k++, i += 2)
            {
                next.put(WallRegistry.cell(data.get(i), data.get(i + 1)), ends);
            }
        }
        cells = next;
        version++;
    }

    /**
     * The colonies linked by the road passing through this cell, or null when no road does.
     */
    public static int[] at(final int x, final int z)
    {
        return cells.get(WallRegistry.cell(x, z));
    }

    /**
     * Every road cell as {x, z, colonyA, colonyB}, for the kingdom map.
     */
    public static List<int[]> cellList()
    {
        final List<int[]> list = new java.util.ArrayList<>(cells.size());
        for (final Map.Entry<Long, int[]> entry : cells.entrySet())
        {
            final long key = entry.getKey();
            list.add(new int[] {(int) (key >> 32), (int) key, entry.getValue()[0], entry.getValue()[1]});
        }
        return list;
    }

    /**
     * Changes each time the roads do, for caches keyed on them.
     */
    public static int version()
    {
        return version;
    }

    public static boolean any()
    {
        return !cells.isEmpty();
    }
}
