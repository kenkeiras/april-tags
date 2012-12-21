#include "vx_matrix_stack.h"
#include "varray.h"
#include "string.h"

struct vx_matrix_stack {

    varray_t * stack;

};

void vx_matrix_stack_get(vx_matrix_stack_t * ms, double out44[16])
{
    double * mat = varray_get(ms->stack, varray_size(ms->stack) -1);
    memcpy(out44, mat, 16*sizeof(double));
}

