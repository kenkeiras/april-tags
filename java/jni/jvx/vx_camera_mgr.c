#include "vx_camera_mgr.h"
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <assert.h>
#include <stdio.h>

#include "vx_util.h"
#include "matd.h"
#include "varray.h"

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


void vx_camera_pos_model_matrix(vx_camera_pos_t * pos, double * out44)
{
    // XXX No scaling implemented as in april.vis

    vx_util_lookat(pos->eye, pos->lookat, pos->up, out44);
}

static inline double sq(double v)
{
    return v*v;
}

static inline double linalg_dist_vec(double * v1, double *v2, int len)
{
    double mag = 0.0;
    for (int i = 0; i < len; i++) {
        mag += sq(v1[i] - v2[i]);
    }

    return sqrt(mag);
}

void vx_camera_pos_projection_matrix(vx_camera_pos_t * pos, double * out44)
{
    varray_t * fp = varray_create();

    int width = pos->viewport[2];
    int height = pos->viewport[3];

    double aspect = ((double) width) / height;
    double dist = linalg_dist_vec(pos->eye, pos->lookat, 3);

    /* vx_util_glu_perspective(); */

    printf("%f %f\n", aspect, dist);//placehold

    matd_t *pM = matd_create(4,4);  varray_add(fp, pM);
    matd_t *oM = matd_create(4,4);  varray_add(fp, oM);

    vx_util_glu_perspective(pos->perspective_fovy_degrees, aspect, pos->zclip_near, pos->zclip_far, pM->data);
    vx_util_gl_ortho(-dist*aspect/2, -dist*aspect/2, -dist/2, dist/2, -pos->zclip_far, pos->zclip_far, oM->data);

    // Virtually all of the visual difference between
    // perspective and orthographic mode occurs when
    // perspectiveness is near zero. As a result, linear
    // interpolation is not very smooth. We rescale the scale
    // factor here to provide a more aesthetically pleasing
    // interpolation.
    double perspectiveness_scaled = pow(pos->perspectiveness, 3);

    matd_t * sp = matd_create_scalar(perspectiveness_scaled);            varray_add(fp, sp);
    matd_t * sp_inv = matd_create_scalar(1.0 - perspectiveness_scaled);  varray_add(fp, sp_inv);

    matd_t * M = matd_op("M*M + M*M", pM, sp, oM, sp_inv); varray_add(fp, M);
    memcpy(out44, M->data, 16*sizeof(double));

    // cleanup
    varray_map(fp, matd_destroy);
    varray_destroy(fp);
}
