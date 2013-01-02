#include "vx_resc_mgr.h"

#include <stdlib.h>
#include <assert.h>

#include "vhash.h"
#include "lphash.h"

#include <stdio.h>

static uint8_t verbose = 0;

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

static void _buffer_map_destroy(vhash_t * bmap)
{
    vhash_map2(bmap, free, lphash_destroy); // won't free vx_resc structs, but that's ok
}

void vx_resc_mgr_destroy(vx_resc_mgr_t * mgr)
{
    // Resources are managed elsewhere, so we actually don't care at
    // all if our resources pointers go stale while they are in the maps.
    // The only thing we really care about are the keys.

    lphash_destroy(mgr->remoteResc); // Will take care of keys, don't care about pointers

    vhash_map2(mgr->allLiveSets, NULL, _buffer_map_destroy);

    free(mgr);
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
    if (verbose) printf("Got %d resources for buffer %s in world %d\n",
                        lphash_size(resources), buffer_name, worldID);

    resources = lphash_copy(resources);
    buffer_name = strdup(buffer_name); // freed with free(prev_name);

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
    if (verbose && prev_resources) printf("  had %d resources previously\n",
                                          lphash_size(prev_resources));


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
            uint64_t wIDl = -1; //XXX if this is an int, vhash breaks!
            vhash_t * buffer_map = NULL;
            while(vhash_iterator_next(&world_itr, &wIDl, &buffer_map)) { // XXXX bug

                vhash_iterator_t buffer_itr; // gives us all buffers
                vhash_iterator_init(buffer_map, &buffer_itr);
                char * bName = NULL;
                lphash_t * resc_map = NULL;
                while(vhash_iterator_next(&buffer_itr, &bName, &resc_map)) {
                    if (lphash_contains(resc_map, id)) {
                        goto continue_outer_loop;
                    }
                }
            }

            // If none of the worlds have this resource, we need to flag removal
            lphash_put(dealloc, id, vr, NULL);
          continue_outer_loop:
            ;
        }

        mgr->rend->remove_resources_direct(mgr->rend, dealloc);

        lphash_destroy(prev_resources);
        free(prev_name);
    }

}
