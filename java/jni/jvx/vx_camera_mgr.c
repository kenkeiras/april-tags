#include "vx_camera_mgr.h"
#include <stdlib.h>

#include <assert.h>

vx_camera_pos_t * vx_camera_pos_create()
{
    vx_camera_pos_t * pos = calloc(1, sizeof(vx_camera_pos_t));
    // XXX
    return pos;
}

void vx_camera_pos_destroy(vx_camera_pos_t * cpos)
{
    // XXX
    return free(cpos);
}


ray3_t * vx_camera_pos_compute_ray(vx_camera_pos_t * pos, vx_mouse_event_t * mouse)
{
    ray3_t * ray = calloc(1, sizeof(ray3_t));
    assert(0);
    return ray;
}
