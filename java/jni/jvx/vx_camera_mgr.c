#include "vx_camera_mgr.h"
#include <stdlib.h>
#include <string.h>

#include <assert.h>

#include "vx_util.h"
#include "matd.h"
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
    double winx = mouse->xy[0];
    double winy = mouse->xy[1];

    double ray0[] = {winx,winy, 0};
    double ray1[] = {winx, winy, 1};


    double mm[16];
    double pm[16];
    vx_camera_pos_model_matrix(pos, mm);
    vx_camera_pos_projection_matrix(pos, pm);


    matd_t * start = matd_create(1,3);
    matd_t * end = matd_create(1,3);

    vx_util_unproject(ray0, mm, pm, pos->viewport, start->data);
    vx_util_unproject(ray1, mm, pm, pos->viewport, end->data);

    matd_t * dir = matd_op("M-M", end, start);

    ray3_t * ray = calloc(1, sizeof(ray3_t));
    memcpy(ray->source, start->data, 3*sizeof(double));
    memcpy(ray->dir, dir->data, 3*sizeof(double));

    matd_destroy(start);
    matd_destroy(end);
    matd_destroy(dir);

    return ray;
}
