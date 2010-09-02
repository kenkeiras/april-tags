package april.vis;

import lcm.lcm.*;
import java.io.*;

/**
 * This colorizer assumes that an RGB value has already been placed
 * in memory inside the 4th position in the point.
 */
public class CachedColorizer implements Colorizer, VisSerializable
{
    public int colorize(double p[])
    {
        return (int) p[3];
    }

    // Serialization for this class is trivial
    public CachedColorizer()
    {
    }

    public void serialize(LCMDataOutputStream out) throws IOException
    {
    }

    public void unserialize(LCMDataInputStream in) throws IOException
    {
    }
}
