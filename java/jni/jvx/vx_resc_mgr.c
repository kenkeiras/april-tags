#include "vx_resc_mgr.h"

#include <stdlib.h>
#include <assert.h>

#include "vhash.h"
#include "lphash.h"

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
    while(lphash_iterator_next(&itr, &id, NULL)) {
        if (lphash_contains(B, id))
            lphash_iterator_remove(&itr);
    }
}

void vx_resc_mgr_update_resources_managed(vx_resc_mgr_t * mgr, int worldID,
                                          char * buffer_name, varray_t * resources_list)
{

    // copy input into a hash set
    lphash_t * send = lphash_create();
    for (int i = 0; i < varray_size(resources_list); i++) {
        vx_resc_t * vr = varray_get(resources_list, i);
        vx_resc_t * old_vr = NULL;
        lphash_put(send, vr->id, vr, &old_vr);
        assert(old_vr == NULL);// err on duplicates for now
    }
    lphash_t *resources = lphash_copy(send); // contains the complete set

    // Remove all elements already on the remote
    removeAll(send, mgr->remoteResc); // XXX Memory leak?

    varray_t * send_vals = lphash_values(send);
    mgr->rend->add_resources_direct(mgr->rend,send_vals);
    varray_destroy(send_vals);

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

        varray_t * dealloc = varray_create();

        lphash_iterator_t prev_itr;
        lphash_iterator_init(prev_resources, &prev_itr);
        uint64_t id = -1;
        vx_resc_t * vr = NULL;
        while(lphash_iterator_next(&prev_itr, &id, &vr)) {
            assert(0);
        }

    }

    // Determine which resources are new, and which need to be sent
    /* vhash_t * world_map = vhash_get(mgr->allLiveSets,worldID); */



}
