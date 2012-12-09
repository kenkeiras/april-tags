package april.vx;

import java.util.*;

// Handels remote rendering
public class VxLCMRenderer extends VxRenderer
{

    public VxLCMRenderer(String url)
    {
        if (!url.startsWith("lcm"))
            throw new IllegalArgumentException("VxLocalRenderer only accepts tcp:// urls");

        // Need to figure out how to define the lcm vx urls. We could ask explicitly for the full LCM url,
        // but it could also be convenient to use the LCM_DEFAULT_URL. Also do we want a channel (suffix) argument?

        // Just blast the resources into lcmx
        // Need to coordinate with the resource manager to make sure that no deallocation occurs, since we can't do anything about that.
    }


    //*** Methods for all VxRenderers ***//

    public void update_resources_managed(int worldID, String name, HashSet<VxResource> resources)
    {
        // Do no resource management for LCM, always send everything
        add_resources_direct(resources);
    }

    // Specifics for handling these messages needs to be determined. Some issues:
    //   1) on lossy comms, it's possible that some resources will never be received by the
    //      the remote. Currently this would cause vx.c to crash
    //   2) how/if to aggregate/split messages
    public void add_resources_direct(HashSet<VxResource> resources)
    {
    }

    public void update_buffer(int worldID, String buffer_name, int drawOrder, VxCodeOutputStream codes)
    {
    }

    // XXX Need to figure out how much of the interface we will support in the LCM case.
    // A reasonable model here would be to never worry about any layer commands -- all objects go
    // into the same vis world, same layer, etc
    public void update_layer(int layerID, int worldID, int draw_order, float viewport_rel[])
    {
        assert(false);
    }

    public void remove_resources_direct(HashSet<VxResource> resources)
    {
        // For the C Side, we'll also need to figure out how to cleanup the memory in use by the resources here?
        assert(false);// For VxLCM, resources are sent each frame, and greedily deallocated after rendering (remotely)
    }

    // Fast for a local implementation
    // XXX This is impossible for LCM Vx
    // Could possibly listen to some LCM channel to get this?
    public int[] get_canvas_size()
    {
        return null;
    }

}