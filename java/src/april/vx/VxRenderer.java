package april.vx;

import java.util.*;

public abstract class VxRenderer
{
    // Methods which all renderers must support
    // Direct mirror of C-side implementation
    public abstract void add_resources(HashSet<VxResource> resources);
    public abstract void update_codes(String buffer_name, int drawOrder, VxCodeOutputStream codes);
    public abstract void remove_resources(HashSet<VxResource> resources);

    // Warning -- this could be very slow, requiring a round-trip to the server
    public abstract int[] get_canvas_size();

    public static VxRenderer make(String url)
    {
        // XXX How do we populate this on the C side, if we want to maintain
        // separation between the core vx library, and gtk, lcm, etc
        if (url.startsWith("java://"))
            return new VxLocalRenderer(url);
        else if (url.startsWith("tcp://"))
            return new VxTCPRenderer(url);
        else if (url.startsWith("lcm"))
            return new VxLCMRenderer(url);

        throw new IllegalArgumentException("Unsupported url: "+url);
    }

}