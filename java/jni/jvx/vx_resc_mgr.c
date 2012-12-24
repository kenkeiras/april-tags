#include "vx_resc_mgr.h"

#include <stdlib.h>
#include <assert.h>

#include "vhash.h"
#include "lphash.h"

#include <stdio.h>

struct vx_resc_mgr
{
    vx_renderer_t * rend;
    vhash_t * allLiveSets; // map< int=worldID, map< char*=buffer_name, map<long=guid, vx_resc_t>>>
    lphash_t * remoteResc; // map <long=guid, vx_resc_t>
};

vx_resc_mgr_t* vx_resc_mgr_create(vx_renderer_t * rend)
{
    vx_resc_mgr_t * mgr = malloc(sizeof(vx_resc_mgr_t));
    mgr->rend = rend;
    mgr->allLiveSets = vhash_create(vhash_uint32_hash, vhash_uint32_equals);
    mgr->remoteResc = lphash_create();
    return mgr;
}

vx_resc_mgr_t* vx_resc_mgr_destroy()
{
    assert(0);
    // Need to intelligently traverse map structure and free the right things.
}


// Remove all elements from A which appear in B
static void removeAll(lphash_t * A, lphash_t  * B)
{
    lphash_iterator_t itr;
    lphash_iterator_init(A, &itr);
    uint64_t id = -1;
    void * value;
    while(lphash_iterator_next(&itr, &id, &value)) {
        /* printf("Processing id %ld\n", id); */
        if (lphash_contains(B, id))
            lphash_iterator_remove(&itr);
    }
}

void vx_resc_mgr_update_resources_managed(vx_resc_mgr_t * mgr, int worldID,
                                          char * buffer_name, lphash_t * resources)
{

    // Step 1: Send only the resources not already sent before:
    lphash_t * send = lphash_copy(resources);
    removeAll(send, mgr->remoteResc); // XXX Memory leak?
    mgr->rend->add_resources_direct(mgr->rend,send);

    // Step 2: Record which resources are currently in use by the named buffer
    vhash_t * worldLiveSet = vhash_get(mgr->allLiveSets, (void *)worldID);
    if (worldLiveSet == NULL) {
        worldLiveSet = vhash_create(vhash_str_hash, vhash_str_equals);
        vhash_put(mgr->allLiveSets, (void*)worldID, worldLiveSet, NULL, NULL);
    }

    char * prev_name = NULL;
    lphash_t * prev_resources = NULL;
    vhash_put(worldLiveSet, buffer_name, resources, &prev_name, &prev_resources); //XXX Need to copy the buffer_name!

    // Find out which of the old resources are no longer referenced, and then deallocate them
    if (prev_resources != NULL) {

        lphash_t * dealloc = lphash_create();

        lphash_iterator_t prev_itr;
        lphash_iterator_init(prev_resources, &prev_itr);
        uint64_t id = -1;
        vx_resc_t * vr = NULL;
        while(lphash_iterator_next(&prev_itr, &id, &vr)) {
            // Check all worlds
            vhash_iterator_t  world_itr;// gives us all worlds
            vhash_iterator_init(mgr->allLiveSets, &world_itr);
            uint32_t wID = -1;
            vhash_t * buffer_map = NULL;
            while(vhash_iterator_next(&world_itr, &wID, &buffer_map)) {
                vhash_iterator_t buffer_itr; // gives us all buffers
                vhash_iterator_init(buffer_map, &buffer_itr);
                char * bName = NULL;
                lphash_t * resc_map = NULL;
                while(vhash_iterator_next(&buffer_itr, &bName, &resc_map)) {
                    if (lphash_contains(resc_map, id))
                        goto continue_outer_loop;
                }
            }

            // If none of the worlds have this resource, we need to flag removal
            lphash_put(dealloc, id, vr, NULL);
          continue_outer_loop:
            ;
        }

        mgr->rend->remove_resources_direct(mgr->rend, dealloc);
    }

}
