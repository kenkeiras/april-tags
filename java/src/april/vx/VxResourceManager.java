package april.vx;


import java.util.*;

// This class implements the resource allocation strategy for managing the remote resources
//

// Beyond this level of abstraction, no one knows whether we are rendering remotely or locally
// In the future, the resource manager could adopt different policies for allocation
// based on the underlying VxRenderer type
public class VxResourceManager
{

    VxRenderer rend;

    // For each buffer, keep track of which guids have been uploaded
    HashMap<String, HashSet<VxResource>> liveSet = new HashMap();

    HashSet<VxResource> remoteResources = new HashSet();


    public VxResourceManager(VxRenderer rend)
    {
        this.rend = rend;
    }

    // Should result in an atomic update to the program database
    // XXX Need to add layer support here?
    public synchronized void swap_buffer(String name, int drawOrder, HashSet<VxResource> resources, VxCodeOutputStream codes)
    {

        // Indicates need for external introspection on C Side...
        // Or the vx_renderer could provide specify the update function when asked
        if (rend instanceof VxLocalRenderer || rend instanceof VxTCPRenderer) {
            update_active_deallocation(name, drawOrder, resources, codes);
        } else if (rend instanceof VxLCMRenderer) {
            update_always_send(name, drawOrder, resources, codes);
        }

    }

    private void update_always_send(String name, int drawOrder, HashSet<VxResource> resources, VxCodeOutputStream codes)
    {
        // rend.remove_resources(null);
        rend.add_resources(resources);
        rend.update_codes(name, drawOrder, codes);
    }

    private void update_active_deallocation(String name, int drawOrder, HashSet<VxResource> resources, VxCodeOutputStream codes)
    {
        // Step 1: Determine which resources are new, and need to be sent:

        liveSet.put(name, resources);

        HashSet<VxResource> send = new HashSet(resources);
        send.removeAll(remoteResources);
        remoteResources.addAll(send);


        // Now remove any stale resources:
        HashSet<VxResource> deallocate = new HashSet();
      outer:
        for (VxResource vresc : remoteResources) {
            for (HashSet<VxResource> live : liveSet.values())
                if (live.contains(vresc))
                    continue outer;

            // if we make it here, we need to release vresc
            deallocate.add(vresc);
        }
        remoteResources.removeAll(deallocate);

        // Push out the changes
        rend.remove_resources(deallocate);
        rend.add_resources(send);
        rend.update_codes(name, drawOrder, codes);
    }
}