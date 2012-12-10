#include "vx_program.h"

#include <stdlib.h>
#include <assert.h>

#include "vhash.h"

struct vx_program_state
{
    vhash_t * attribMap; // and many more
};

static vx_program_state_t * vx_program_state_create()
{
    vx_program_state_t * state = malloc(sizeof(vx_program_state_t));
    state->attribMap = vhash_create(vhash_str_hash, vhash_str_equals);
    return state;
}


static void vx_program_append(vx_object_t * obj, vhash_t * resources, vx_code_output_stream_t * output, vx_matrix_stack_t * ms)
{
    // XXX do nothing
    assert(0);
}


vx_program_t * vx_program_create(vx_resc_t * vert_src, vx_resc_t * frag_src)
{
    vx_program_t * program = malloc(sizeof(vx_program_t));
    vx_object_t * obj = malloc(sizeof(vx_object_t));
    obj->append = vx_program_append;
    obj->impl = program;
    program->super = obj;
    program->state = vx_program_state_create();

    return program;
}
