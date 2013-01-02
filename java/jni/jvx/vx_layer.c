#include "vx_layer.h"
#include <stdlib.h>

struct vx_layer
{
    int layerID;
    vx_renderer_t * rend;
    vx_world_t * world;
    int drawOrder;
    float viewport_rel[4];
};

static int xxxAtomicID = 1;

static void update(vx_layer_t * vl)
{
    vl->rend->update_layer(vl->rend, vl->layerID,
                           vx_world_get_id(vl->world),
                           vl->drawOrder, vl->viewport_rel);
}

vx_layer_t * vx_layer_create(vx_renderer_t * rend, vx_world_t * world)
{

    vx_layer_t * vl = calloc(1,sizeof(vx_layer_t));
    vl->layerID = xxxAtomicID++;
    vl->rend = rend;
    vl->world = world;
    vl->drawOrder = 0;
    vl->viewport_rel[0]  = vl->viewport_rel[1] = 0.0f;
    vl->viewport_rel[2]  = vl->viewport_rel[3] = 1.0f; // Full screen by default


    // XXXX Event handling

    update(vl);

    return vl;
}

void vx_layer_destroy(vx_layer_t * layer)
{
    free(layer);
}
