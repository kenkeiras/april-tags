#include "vx_layer.h"
#include <stdlib.h>
#include "default_camera_mgr.h"
#include "default_event_handler.h"

struct vx_layer
{
    int layer_id;
    vx_renderer_t * rend;
    vx_world_t * world;
    int draw_order;
    float viewport_rel[4];

    vx_camera_mgr_t * camera_mgr;
    varray_t * event_handlers;
};

static int xxxAtomicID = 1;

static void update(vx_layer_t * vl)
{
    vl->rend->update_layer(vl->rend, vl->layer_id,
                           vx_world_get_id(vl->world),
                           vl->draw_order, vl->viewport_rel);
}

static void vx_event_handler_destroy(vx_event_handler_t * eh)
{
    eh->destroy(eh);
}

vx_layer_t * vx_layer_create(vx_renderer_t * rend, vx_world_t * world)
{

    vx_layer_t * vl = calloc(1,sizeof(vx_layer_t));
    vl->layer_id = xxxAtomicID++;
    vl->rend = rend;
    vl->world = world;
    vl->draw_order = 0;
    vl->viewport_rel[0]  = vl->viewport_rel[1] = 0.0f;
    vl->viewport_rel[2]  = vl->viewport_rel[3] = 1.0f; // Full screen by default


    // XXXX Event handling
    vl->camera_mgr = vx_camera_mgr_default_create();
    vl->event_handlers = varray_create();

    varray_add(vl->event_handlers, default_event_handler_create());
    update(vl);

    return vl;
}

void vx_layer_destroy(vx_layer_t * layer)
{
    layer->camera_mgr->destroy(layer->camera_mgr);
    varray_map(layer->event_handlers, vx_event_handler_destroy);
    varray_destroy(layer->event_handlers); // XXX Memory leak -- event handlers will be leaked!
    free(layer);
}

int  vx_layer_id(vx_layer_t * vl)
{
    return vl->layer_id;
}

vx_camera_mgr_t * vx_layer_camera_mgr(vx_layer_t * vl)
{
    return vl->camera_mgr;
}

int * vx_layer_viewport_abs(vx_layer_t * vl, int width, int height)
{
    int * viewport = malloc(4*sizeof(int));
    viewport[0] = (int)(vl->viewport_rel[0] * width);
    viewport[1] = (int)(vl->viewport_rel[1] * height);
    viewport[2] = (int)(vl->viewport_rel[2] * width);
    viewport[3] = (int)(vl->viewport_rel[3] * height);
    return viewport;
}

int vx_layer_comparator(const void * a, const void * b)
{
    return    ((vx_layer_t*)a)->draw_order - ((vx_layer_t*)b)->draw_order;
}


int vx_layer_dispatch_mouse(vx_layer_t * vl, vx_camera_pos_t * pos, vx_mouse_event_t * mouse)
{
    return 0;
}

int vx_layer_dispatch_key(vx_layer_t * vl, vx_key_event_t * mouse)
{
    return 0;
}
