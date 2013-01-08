#include "vx_util.h"
#include <stdlib.h>
#include <sys/time.h>
#include "matd.h"
#include "varray.h"
#include <assert.h>
#include <math.h>

uint64_t xxxAtomicLong = 1; //XX XX

uint64_t vx_alloc_id()
{
    return xxxAtomicLong++;
}


uint64_t vx_mtime()
{
    // get the current time
    struct timeval tv;
    gettimeofday (&tv, NULL);
    return (int64_t) tv.tv_sec * 1000 + tv.tv_usec/1000;
}


void vx_util_unproject(double * winxyz, double * model_matrix, double * projection_matrix, int * viewport, double * vec3_out)
{
    varray_t * fp = varray_create();

    matd_t * mm = matd_create_data(4, 4, model_matrix);
    varray_add(fp,mm);
    matd_t * pm = matd_create_data(4, 4, projection_matrix);
    varray_add(fp, pm);

    matd_t *invpm = matd_op("(MM)^-1", mm, pm);
    varray_add(fp, invpm);

    double v[4] = { 2*(winxyz[0]-viewport[0]) / viewport[2] - 1,
                    2*(winxyz[1]-viewport[1]) / viewport[3] - 1,
                    2*winxyz[2] - 1,
                    1 };
    matd_t * vm = matd_create_data(4, 1, v);
    varray_add(fp, vm);

    matd_t * objxyzh = matd_op("MM", invpm, vm);
    varray_add(fp,objxyzh);

    vec3_out[0] = objxyzh->data[0] / objxyzh->data[3];
    vec3_out[1] = objxyzh->data[1] / objxyzh->data[3];
    vec3_out[2] = objxyzh->data[2] / objxyzh->data[3];

    // cleanup
    varray_map(fp, matd_destroy);
    varray_destroy(fp);
}


void vx_util_glu_perspective(double fovy_degrees, double aspect, double znear, double zfar, double * M)
{
    double fovy_rad = fovy_degrees * M_PI / 180.0;
    double f = 1.0 / tan(fovy_rad/2);

    M[0*4 + 0] = f/aspect;
    M[1*4 + 1] = f;
    M[2*4 + 2] = (zfar+znear)/(znear-zfar);
    M[2*4 + 3] = 2*zfar*znear / (znear-zfar);
    M[3*4 + 2] = -1;
}

void vx_util_glu_ortho(double left, double right, double bottom, double top, double znear, double zfar, double * M)
{
    M[0*4 + 0] = 2 / (right - left);
    M[0*4 + 3] = -(right+left)/(right-left);
    M[1*4 + 1] = 2 / (top-bottom);
    M[1*4 + 3] = -(top+bottom)/(top-bottom);
    M[2*4 + 2] = -2 / (zfar - znear);
    M[2*4 + 3] = -(zfar+znear)/(zfar-znear);
    M[3*4 + 3] = 1;
}

void vx_util_lookat(double * eye, double * lookat, double * up, double * out44)
{
    assert(0);
}
