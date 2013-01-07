#ifndef __VX_LAYER
#define __VX_LAYER

#include "vx_renderer.h"
#include "vx_world.h"
#include "vx_camera_mgr.h"

// NOTE: unlike vx_object and vx_resc, you must manually manage the lifetime of vx_world, vx_layer, vx_canvas and vx_renderer

typedef struct vx_layer vx_layer_t;// forward reference

vx_layer_t * vx_layer_create(vx_renderer_t * rend, vx_world_t * world);

void vx_layer_destroy(vx_layer_t * layer);

int vx_layer_comparator(const void * a, const void * b);

// compute the absolute viewport size, in pixels. Caller is responsible for
// freeing the int array, length = 4
int * vx_layer_viewport_abs(vx_layer_t * vl, int width, int height);
int  vx_layer_id(vx_layer_t * vl);


vx_camera_mgr_t * vx_layer_camera_mgr(vx_layer_t * vl);
#endif
