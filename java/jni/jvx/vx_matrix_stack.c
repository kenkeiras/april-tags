#include "vx_matrix_stack.h"
#include "varray.h"
#include "string.h"
#include "stdlib.h"

struct vx_matrix_stack {

    varray_t * stack;
    double M[16];
};

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
    varray_destroy(ms->stack);
    free(ms);
}
