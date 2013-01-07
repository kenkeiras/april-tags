#ifndef VX_CAMERA_MGR_H
#define VX_CAMERA_MGR_H

#include "ray3.h"
#include "vx_event.h"

typedef struct
{
    double pos[3];
    int viewport[4];
} vx_camera_pos_t; // XXX get's it's own file?


typedef struct vx_camera_mgr vx_camera_mgr_t;
struct vx_camera_mgr {

    // returns a copy which user is responsible for destroying
    vx_camera_pos_t * (* get_camera_pos)( vx_camera_mgr_t * vxcam, int * viewport, uint64_t mtime);
    void (* destroy)(vx_camera_mgr_t * vxcam);
    void * impl;

};

vx_camera_pos_t * vx_camera_pos_create();
void vx_camera_pos_destroy(vx_camera_pos_t * pos);
ray3_t * vx_camera_pos_compute_ray(vx_camera_pos_t * pos, vx_mouse_event_t * mouse);

#endif
