#ifndef VX_CAMERA_MGR_H
#define VX_CAMERA_MGR_H


typedef struct
{
    double pos[3];
} vx_camera_pos_t; // XXX get's it's own file?


typedef struct vx_camera_mgr vx_camera_mgr_t;
struct vx_camera_mgr {
    vx_camera_pos_t * (* get_camera_pos)( vx_camera_mgr_t * vxcam);
    void (* destroy)(vx_camera_mgr_t * vxcam);
    void * impl;

};

#endif
