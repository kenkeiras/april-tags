package april.vx;

import java.util.*;

// Utility class to keep track of which resources have already been sent over
class VxResourceManager
{
    VxRenderer rend;

    // For world and each buffer, keep track of which guids have been uploaded
    HashMap<Long, HashMap<String, HashSet<VxResource>>> allLiveSets = new HashMap();
    HashSet<VxResource> remoteResources = new HashSet();

    protected VxResourceManager(VxRenderer rend)
    {
        this.rend = rend;
    }

    protected void update_resources_managed(long worldId, String name, HashSet<VxResource> resources)
    {
        // Step 1: Determine which resources are new, and need to be sent:
        HashSet<VxResource> send = new HashSet(resources); // copy
        send.removeAll(remoteResources);
        remoteResources.addAll(send);

        // push out new resources
        rend.add_resources_direct(send);

        // Step 2: Record which resources are currently in use by the named buffer:
        HashMap<String, HashSet<VxResource>> worldLiveSet = allLiveSets.get(worldId);
        if (worldLiveSet == null) {
            worldLiveSet = new HashMap();
            allLiveSets.put(worldId, worldLiveSet);
        }
        HashSet<VxResource> oldResources = worldLiveSet.get(name); // could be null
        if (oldResources == null)
            oldResources = new HashSet(); // make an empty list
        worldLiveSet.put(name, resources);


        // Step 2.5: For any resource which used to be in this buffer,
        // but is no longer used here, we need to check if it is being
        // used anywhere else. If not, we should flag for removal
        oldResources.removeAll(resources);

        HashSet<VxResource> deallocate = new HashSet();
      outer:
        for (VxResource vresc : oldResources) {
            for (HashMap<String, HashSet<VxResource>> world : allLiveSets.values())
                for (HashSet<VxResource> live : world.values())
                    if (live.contains(vresc))
                        continue outer;
            // if we make it here, we need to release vresc
            deallocate.add(vresc);
        }
        remoteResources.removeAll(deallocate);

        // Push out the changes
        rend.remove_resources_direct(deallocate);
    }
}
