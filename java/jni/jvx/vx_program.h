#ifndef __VX_PROGRAM_
#define __VX_PROGRAM_

#include "vx_object.h"
#include "vx_resc.h"

// forward reference
typedef struct vx_program_state vx_program_state_t;

typedef struct vx_program vx_program_t;
struct vx_program
{
    vx_object_t *super;
    vx_program_state_t *state;
};


vx_program_t * vx_program_create(vx_resc_t * vert_src, vx_resc_t * frag_src);
//XXXX Destroy?

void vx_program_set_draw_array(vx_program_t * program, int count, int type);
void vx_program_set_element_array(vx_program_t * program, vx_resc_t * indices, int type);
void vx_program_set_vertex_attrib(vx_program_t * program, char * name, vx_resc_t * attrib, int dim);
void vx_program_set_uniform4fv(vx_program_t * program, char * name, float * vec4);

//void vx_program_set_uniform_matrix4fv(vx_program_t * program, char * name, float * mat44);



#endif
