#ifndef __VX_LAYER
#define __VX_LAYER

#include "vx_renderer.h"
#include "vx_world.h"

typedef struct vx_layer vx_layer_t;// forward reference

vx_layer_t * vx_layer_create(vx_renderer_t * rend, vx_world_t * world);
void vx_layer_destroy(vx_layer_t * layer);

#endif
