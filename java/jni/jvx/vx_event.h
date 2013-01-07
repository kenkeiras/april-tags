#ifndef VX_EVENT_H
#define VX_EVENT_H

#include <stdint.h>

#include "vx_codes.h"

typedef struct vx_key_event vx_key_event_t;
typedef struct vx_mouse_event vx_mouse_event_t;

struct vx_key_event
{
    uint32_t modifiers;
    uint32_t key_code;
    uint32_t released; // 1 if key is released, 0 if pressed
};

struct vx_mouse_event
{
    double xy[2];
    uint32_t button_mask; // which mouse buttons were down?
    uint32_t scroll_amt; // negative if away(up/left) , positive if towards(down/right)
    uint32_t modifiers;
};

#endif
