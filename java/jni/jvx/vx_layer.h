#ifndef __VX_LAYER
#define __VX_LAYER


// NOTE: unlike vx_object and vx_resc, you must manually manage the lifetime of vx_world, vx_layer, vx_canvas and vx_renderer

#include "vx_renderer.h"
#include "vx_world.h"
#include "vx_camera_mgr.h"
#include "vx_event_handler.h"

typedef struct vx_layer vx_layer_t;// forward reference
typedef struct vx_event_handler vx_event_handler_t; // forward reference

vx_layer_t * vx_layer_create(vx_renderer_t * rend, vx_world_t * world);

void vx_layer_destroy(vx_layer_t * layer);

int vx_layer_comparator(const void * a, const void * b);

// compute the absolute viewport size, in pixels. Caller is responsible for
// freeing the int array, length = 4
int * vx_layer_viewport_abs(vx_layer_t * vl, int width, int height);
int  vx_layer_id(vx_layer_t * vl);

vx_camera_mgr_t * vx_layer_camera_mgr(vx_layer_t * vl);
void vx_layer_add_event_handler(vx_layer_t * vl, vx_event_handler_t * eh);

// For use by vx_canvas when dispatching events. Returns 1 if event was consumed, 0 otherwise
int vx_layer_dispatch_mouse(vx_layer_t * vl, vx_camera_pos_t * pos, vx_mouse_event_t * mouse);
int vx_layer_dispatch_key(vx_layer_t * vl, vx_key_event_t * mouse);

#endif
