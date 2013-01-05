#include "vx_matrix_stack.h"
#include "varray.h"
#include "string.h"
#include "stdlib.h"

struct vx_matrix_stack {

    varray_t * stack;
    double M[16];
};

// C = A*B
static void mult44(double * A, double * B, double * C)
{
    for (int i = 0; i < 4; i++) {
        for (int j = 0; j < 4; j++) {
            double acc = 0.0f;
            for (int k = 0; k < 4; k++)
                acc += A[i*4 + k] * B[k*4 + j];
            C[i*4 +j] = acc;
        }
    }
}

void vx_matrix_stack_get(vx_matrix_stack_t * ms, double out44[16])
{
    memcpy(out44, ms->M, 16*sizeof(double));
}

void vx_matrix_stack_ident(vx_matrix_stack_t * ms)
{
    for (int i = 0; i < 4; i++)
        for (int j = 0; j < 4; j++)
            ms->M[i*4+j] = (i == j ? 1.0 : 0.0);
}

vx_matrix_stack_t * vx_matrix_stack_create()
{
    vx_matrix_stack_t * ms = malloc(sizeof(vx_matrix_stack_t));
    ms->stack = varray_create();
    vx_matrix_stack_ident(ms);
    return ms;
}

void vx_matrix_stack_destroy(vx_matrix_stack_t * ms)
{
    varray_map(ms->stack, free);
    varray_destroy(ms->stack);
    free(ms);
}


void vx_matrix_stack_mult(vx_matrix_stack_t * ms, double * in44)
{
    double temp[16];
    memcpy(temp, ms->M, 16*sizeof(double)); // from M to temp
    mult44(temp, in44, ms->M); // mult equals
}

void vx_matrix_stack_push(vx_matrix_stack_t * ms)
{
    double * mat44 = calloc(16, sizeof(double));
    memcpy(mat44, ms->M, 16*sizeof(double)); // from M to mat44
    varray_add(ms->stack, mat44);

}

void vx_matrix_stack_pop(vx_matrix_stack_t * ms)
{
    double *mat44  = varray_remove(ms->stack, varray_size(ms->stack)-1);
    memcpy(ms->M, mat44, 16*sizeof(double)); // from mat44 to M
    free(mat44);
}
