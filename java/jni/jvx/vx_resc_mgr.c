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

void vx_resc_mgr_update_resources_managed(vx_resc_mgr_t * mgr, int worldID,
                                          char * buffer_name, varray_t * resources)
{

    // copy input into a hash set
    lphash_t * send = lphash_create();
    for (int i = 0; i < varray_size(resources); i++) {
        vx_resc_t * vr = varray_get(resources, i);
        lphash_put(send, vr->id, vr);
    }

    // Remove the resources which are already on the remote:
    {
        lphash_iterator_t itr;
        lphash_iterator_init(send, &itr);
        while(lphash_iterator_has_next(send, &itr)) {
            uint64_t id = lphash_iterator_next_key(send, &itr);


            vx_resc_t * vr = lphash_get(mgr->remoteResc, id);
            if (vr != NULL) {
                /* lphash_iterator_remove(send, &itr); */
            }
        }
    }

    // Determine which resources are new, and which need to be sent
    /* vhash_t * world_map = vhash_get(mgr->allLiveSets,worldID); */



}
