package april.vx;

import java.util.*;

// Handels remote rendering
public class VxTCPRenderer extends VxRenderer
{

    public VxTCPRenderer(String url)
    {
        if (!url.startsWith("tcp://"))
            throw new IllegalArgumentException("VxLocalRenderer only accepts tcp:// urls");

        // XXX don't make a gtk_remote renderer, even though we probably could


        // bind a connection with specified address

        // Setup a FIFO queue for incoming resource additions, code
        // updates, or deallocations to preserve order on other side
    }


    //*** Methods for all VxRenderers ***//
    public void add_resources(HashSet<VxResource> resources)
    {
    }
    public void update_codes(String buffer_name, VxCodeOutputStream codes)
    {
    }
    public void remove_resources(HashSet<VxResource> resources)
    {

    }

    // Fast for a local implementation
    public int[] get_canvas_size()
    {
        return new int[]{0,0}; //XXX
    }

}