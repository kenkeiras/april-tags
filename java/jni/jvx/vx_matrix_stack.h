#ifndef __VX_MATRIX_STACK_
#define __VX_MATRIX_STACK_


typedef struct vx_matrix_stack vx_matrix_stack_t;

// memory management: copy everything!

void vx_matrix_stack_ident(vx_matrix_stack_t * ms);
void vx_matrix_stack_mult(vx_matrix_stack_t * ms, double in44[16]);
void vx_matrix_stack_push(vx_matrix_stack_t * ms);
void vx_matrix_stack_pop(vx_matrix_stack_t * ms);
void vx_matrix_stack_get(vx_matrix_stack_t * ms, double out44[16]);

vx_matrix_stack_t * vx_matrix_stack_create();
void vx_matrix_stack_destroy(vx_matrix_stack_t * ms);

#endif
