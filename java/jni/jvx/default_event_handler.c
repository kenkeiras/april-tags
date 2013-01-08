#include "default_event_handler.h"
#include <assert.h>
#include <stdlib.h>
#include <stdio.h>

#include "vx_codes.h"

typedef struct {
    vx_mouse_event_t * last_mouse_event;
    double manipulation_point[3];

} default_event_handler_t;

static int default_mouse_event(vx_event_handler_t * vh, vx_layer_t * vl, vx_camera_pos_t * pos, vx_mouse_event_t * mouse)
{
    printf("mouse\n");

    default_event_handler_t * deh = (default_event_handler_t*) vh->impl;

    ray3_t * ray = vx_camera_pos_compute_ray(pos, mouse);

    int shift = mouse->modifiers & VX_SHIFT_MASK;
    int ctrl = mouse->modifiers & VX_CTRL_MASK;

    if (mouse->button_mask & VX_BUTTON1_MASK) { // panning
        if (shift || ctrl)
            return 0;

        ray3_intersect_xy(ray, 0.0, deh->manipulation_point); // XXX No manipulation manager right now
        printf ("%.2f, %.2f, %.2f\n", deh->manipulation_point[0], deh->manipulation_point[1], deh->manipulation_point[2]);
    }

    return 0;
}

static int default_key_event(vx_event_handler_t * vh, vx_layer_t * vl, vx_key_event_t * mouse)
{
    printf("key\n");
    return 0;
}

static void default_destroy(vx_event_handler_t * vh)
{
    free(vh->impl);
    free(vh);
}

static default_event_handler_t * default_state_create()
{
    default_event_handler_t * deh = calloc(1,sizeof(default_event_handler_t));
    return deh;
}

vx_event_handler_t * default_event_handler_create()
{
    vx_event_handler_t * eh = calloc(1, sizeof(vx_event_handler_t));
    eh->mouse_event = default_mouse_event;
    eh->key_event = default_key_event;
    eh->destroy = default_destroy;
    eh->impl = default_state_create();

    return eh;
}
