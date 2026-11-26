package fr.kodistan.interregne.dev;

import com.minecolonies.api.colony.IColony;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * A gameplay check run by {@link ScenarioRunner}: put a colony in a known state, let the game run fast, judge the result.
 */
public interface Scenario
{
    /**
     * Put the colony in the starting state.
     *
     * @return lines describing what was set up.
     * @throws IllegalStateException when the colony lacks what the scenario needs (the message says what).
     */
    List<String> setup(IColony colony, ServerLevel level);

    /**
     * Called every server tick while the game runs, for checks that need to watch a state change as it happens.
     */
    default void tick(final IColony colony, final ServerLevel level, final long tick)
    {
    }

    /**
     * One line of measures, taken at regular intervals while the game runs.
     */
    String snapshot(IColony colony, ServerLevel level);

    /**
     * The checks at the end, each line starting with PASS or FAIL.
     */
    List<String> verdict(IColony colony, ServerLevel level);
}
