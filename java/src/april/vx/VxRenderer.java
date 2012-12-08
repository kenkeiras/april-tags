package april.vx;

import java.util.*;

public abstract class VxRenderer
{
    // Methods which all renderers must support
    // Direct mirror of C-side implementation

    // Resource management: Caller is responsible to ensure resources are set before referenced in render codes.
    //   update_resources() requires local access to all resources, and then removes duplicates
    //   add_resources() only requires access to new resources, used for remote management

    // Declare the exact resources needed to render this buffer. Will cause add_resource() calls for new resources
    public abstract void update_resources_managed(int worldID, String buffer_name, HashSet<VxResource> resources);

    // upload/remove resources without checking for duplicates.
    // These method should only be called from VxRenderers, not from any other Vx class
    protected abstract void add_resources_direct(HashSet<VxResource> resources);
    protected abstract void remove_resources_direct(HashSet<VxResource> resources);

    // Detail a new set of render codes for a specific buffer
    public abstract void update_buffer(int worldID, String buffer_name, int drawOrder, VxCodeOutputStream codes);

    // Set the viewport and worldID for a specific layer
    public abstract void update_layer(int layerID, int worldID, float viewport_rel[]);


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