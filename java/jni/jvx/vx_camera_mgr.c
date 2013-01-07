#include "vx_camera_mgr.h"
#include <stdlib.h>


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
