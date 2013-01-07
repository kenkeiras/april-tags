#ifndef __VX_LAYER
#define __VX_LAYER

#include "vx_renderer.h"
#include "vx_world.h"

// NOTE: unlike vx_object and vx_resc, you must manually manage the lifetime of vx_world, vx_layer, vx_canvas and vx_renderer

typedef struct vx_layer vx_layer_t;// forward reference

vx_layer_t * vx_layer_create(vx_renderer_t * rend, vx_world_t * world);
void vx_layer_destroy(vx_layer_t * layer);

#endif
