#ifndef VX_EVENT_HANDLER_H
#define VX_EVENT_HANDLER_H

#include "vx_event.h"
#include "vx_camera_mgr.h"
#include "vx_layer.h"

typedef struct vx_layer vx_layer_t; // forward reference

typedef struct vx_event_handler vx_event_handler_t;
struct vx_event_handler
{
    int dispatch_order; // determines which order the event handlers will called in. Lower numbers get first dibs

    // return 0 if event was consumed, 1 otherwise
    int (*mouse_event)(vx_event_handler_t * vh, vx_layer_t * vl, vx_camera_pos_t * pos, vx_mouse_event_t * mouse);
    int (*key_event)(vx_event_handler_t * vh, vx_layer_t * vl, vx_key_event_t * mouse);
    void (*destroy)(vx_event_handler_t * vh);
    void * impl;
};

#endif
