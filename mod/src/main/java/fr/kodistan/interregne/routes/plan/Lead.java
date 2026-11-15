package fr.kodistan.interregne.routes.plan;

/**
 * A fixed straight approach to a route's end: the road runs out from the threshold ({@code x}, {@code z}) along the
 * outward normal ({@code nx}, {@code nz}) for {@code length} cells at the floor level {@code y} before the search
 * takes over, so it meets a gate square on and narrows into its passage.
 */
public record Lead(int x, int z, int nx, int nz, int length, int y)
{
    public int outX()
    {
        return x + nx * length;
    }

    public int outZ()
    {
        return z + nz * length;
    }
}
