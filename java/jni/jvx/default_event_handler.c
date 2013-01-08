#include "default_event_handler.h"
#include <assert.h>
#include <stdlib.h>
#include <stdio.h>

static int default_mouse_event(vx_event_handler_t * vh, vx_layer_t * vl, vx_camera_pos_t * pos, vx_mouse_event_t * mouse)
{
    printf("mouse\n");
    return 0;
}

static int default_key_event(vx_event_handler_t * vh, vx_layer_t * vl, vx_key_event_t * mouse)
{
    printf("key\n");
    return 0;
}

static void default_destroy(vx_event_handler_t * vh)
{
    free(vh);
}

vx_event_handler_t * default_event_handler_create()
{
    vx_event_handler_t * eh = calloc(1, sizeof(vx_event_handler_t));
    eh->mouse_event = default_mouse_event;
    eh->key_event = default_key_event;
    eh->destroy = default_destroy;
    eh->impl = NULL;//default_state_create

    return eh;
}
