#include "vx_mat.h"

#include <stdlib.h>
#include <string.h>

static void vx_mat_append (vx_object_t * obj, lphash_t * resources, vx_code_output_stream_t * codes, vx_matrix_stack_t * ms)
{
    vx_matrix_stack_mult(ms, (double*) obj->impl);
}

static void vx_mat_destroy(vx_object_t *vo)
{
    free(vo->impl);
    free(vo);
}

static vx_object_t * vx_mat_create()
{
    vx_object_t * vo = calloc(1, sizeof(vx_object_t));
    vo->destroy = vx_mat_destroy;
    vo->append = vx_mat_append;
    vo->impl = calloc(16, sizeof(double));

    // initialize to identity
    double * mat44 = vo->impl;
    for (int i = 0; i < 4; i++)
        mat44[i*4+i] = 1.0;

    return vo;
}

vx_object_t * vx_mat_translate(double x, double y, double z)
{
    vx_object_t * obj = vx_mat_create();
    double * mat44 = obj->impl;
    mat44[0*4 + 3] = x;
    mat44[1*4 + 3] = y;
    mat44[2*4 + 3] = z;
    return obj;
}

vx_object_t * vx_mat_copy_from_doubles(double * mat44)
{
    vx_object_t * obj = vx_mat_create();
    memcpy(obj->impl, mat44, 16*sizeof(double));
    return obj;
}

