#include "vx_util.h"
#include <stdlib.h>
#include <sys/time.h>
#include "matd.h"
#include "varray.h"
#include <assert.h>
#include <math.h>
#include <string.h>

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

void vx_util_gl_ortho(double left, double right, double bottom, double top, double znear, double zfar, double * M)
{
    M[0*4 + 0] = 2 / (right - left);
    M[0*4 + 3] = -(right+left)/(right-left);
    M[1*4 + 1] = 2 / (top-bottom);
    M[1*4 + 3] = -(top+bottom)/(top-bottom);
    M[2*4 + 2] = -2 / (zfar - znear);
    M[2*4 + 3] = -(zfar+znear)/(zfar-znear);
    M[3*4 + 3] = 1;
}

void vx_util_lookat(double * _eye, double * _lookat, double * _up, double * _out44)
{
    varray_t * fp = varray_create();

    matd_t * eye = matd_create_data(3,1, _eye);
    varray_add(fp, eye);

    matd_t * lookat = matd_create_data(3,1, _lookat);
    varray_add(fp, lookat);

    matd_t * up = matd_create_data(3,1, _up);
    varray_add(fp, up);

    up = matd_vec_normalize(up);
    varray_add(fp, up); // note different pointer than before!

    matd_t * tmp1 = matd_subtract(lookat, eye); varray_add(fp, tmp1);
    matd_t * f = matd_vec_normalize(tmp1);      varray_add(fp, f);
    matd_t * s = matd_crossproduct(f, up);      varray_add(fp, s);
    matd_t * u = matd_crossproduct(s, f);       varray_add(fp, u);

    matd_t * M = matd_create(4,4); // set the rows of M with s, u, -f
    varray_add(fp, M);
    memcpy(M->data,s->data,3*sizeof(double));
    memcpy(M->data + 4,u->data,3*sizeof(double));
    memcpy(M->data + 8,f->data,3*sizeof(double));
    for (int i = 0; i < 3; i++)
        M->data[3*4 +i] *= -1;
    M->data[3*4 + 3] = 1.0;

    matd_t * T = matd_create(4,4);
    T->data[0*4 + 3] = -eye->data[0];
    T->data[1*4 + 3] = -eye->data[1];
    T->data[2*4 + 3] = -eye->data[2];
    T->data[0*4 + 0] = 1;
    T->data[1*4 + 1] = 1;
    T->data[2*4 + 2] = 1;
    T->data[3*4 + 3] = 1;
    varray_add(fp, T);

    matd_t * MT = matd_op("MM",M,T);
    varray_add(fp, MT);


    memcpy(_out44, MT->data, 16*sizeof(double));

    // cleanup
    varray_map(fp, matd_destroy);
    varray_destroy(fp);
}
