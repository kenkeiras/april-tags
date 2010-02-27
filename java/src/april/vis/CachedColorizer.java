package april.vis;

/**
 * This colorizer assumes that an RGB value has already been placed
 * in memory inside the 4th position in the point.
 */
public class CachedColorizer implements Colorizer
{
    public int colorize(double p[])
    {
        return (int) p[3];
    }
}
