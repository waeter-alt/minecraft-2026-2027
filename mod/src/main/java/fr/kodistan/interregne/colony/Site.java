package fr.kodistan.interregne.colony;

/**
 * A timed, animated colony work (construction or demolition) run by {@link ConstructionManager}.
 */
interface Site
{
    /**
     * Opening effects, called once when the site is registered.
     */
    void start();

    /**
     * Advance one server tick.
     *
     * @return true once the work is over and the site can be dropped.
     */
    boolean tick();

    /**
     * Complete the work at once, used when the server stops so nothing is left half done.
     */
    void finishNow();
}
