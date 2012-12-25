#include "vx_program.h"

#include <stdlib.h>
#include <assert.h>

#include "vx_codes.h"
#include "vhash.h"

struct vx_program_state
{
    vx_resc_t * vert;
    vx_resc_t * frag;


    vhash_t * attribMap; // and many more
    int draw_type;
    int draw_count; // if draw_array
    vx_resc_t * indices; // if element_array
};


typedef struct _vertex_attrib _vertex_attrib_t;
struct _vertex_attrib
{
    vx_resc_t * vr; // XXX how to manage the lifetime?
    int dim; // how many 'types' per vertex. e.g. 3 for xyz data
    char * name;
};

static vx_program_state_t * vx_program_state_create()
{
    vx_program_state_t * state = malloc(sizeof(vx_program_state_t));
    state->attribMap = vhash_create(vhash_str_hash, vhash_str_equals);
    state->draw_type = -1;
    state->draw_count = -1;
    state->indices = NULL;

    return state;
}


static void vx_program_append(vx_object_t * obj, lphash_t * resources, vx_code_output_stream_t * codes, vx_matrix_stack_t * ms)
{
    // Safe because this function is only assigned to vx_program types
    vx_program_state_t * state = ((vx_program_t *)obj->impl)->state;

    codes->write_uint32(codes, OP_PROGRAM);
    codes->write_uint64(codes, state->vert->id);
    codes->write_uint64(codes, state->frag->id);

    lphash_put(resources, state->vert->id, state->vert, NULL); // XXX return values?
    lphash_put(resources, state->frag->id, state->frag, NULL);

    codes->write_uint32(codes, OP_VALIDATE_PROGRAM);
    codes->write_uint32(codes, 1);

    codes->write_uint32(codes, OP_MODEL_MATRIX_44);
    double modelMat[16];
    vx_matrix_stack_get(ms, modelMat);
    for (int i = 0; i < 4; i++) {
        for (int j = 0; j < 4; j++) {
            codes->write_float(codes, (float)modelMat[i*4 +j]);
        }
    }
    codes->write_uint32(codes, OP_PM_MAT_NAME);
    codes->write_str(codes, "PM"); // XXX hardcoded


    codes->write_uint32(codes, OP_VERT_ATTRIB_COUNT);
    codes->write_uint32(codes, vhash_size(state->attribMap));
    {
        vhash_iterator_t itr;
        vhash_iterator_init(state->attribMap, &itr);

        char * key = NULL;
        _vertex_attrib_t * value = NULL;
        while(vhash_iterator_next(&itr, &key, &value)) {
            codes->write_uint32(codes, OP_VERT_ATTRIB);
            codes->write_uint64(codes, value->vr->id);
            codes->write_uint32(codes, value->dim);
            codes->write_str(codes,  value->name);

            lphash_put(resources, value->vr->id, value->vr, NULL); //XXX Existing values?
        }
    }
    codes->write_uint32(codes, OP_UNIFORM_COUNT);
    codes->write_uint32(codes, 0); // XXX Unsupported

    codes->write_uint32(codes, OP_TEXTURE_COUNT);
    codes->write_uint32(codes, 0); // XXX Unsupported


    // bind drawing instructions
    if (state->indices != NULL) {
        // Element array
        assert(0);
    } else {
        // draw array
        codes->write_uint32(codes, OP_DRAW_ARRAY);
        codes->write_uint32(codes, state->draw_count);
        codes->write_uint32(codes, state->draw_type);
    }

}


vx_program_t * vx_program_create(vx_resc_t * vert_src, vx_resc_t * frag_src)
{
    vx_program_t * program = malloc(sizeof(vx_program_t));
    vx_object_t * obj = malloc(sizeof(vx_object_t));
    obj->append = vx_program_append;
    obj->impl = program;
    program->super = obj;
    program->state = vx_program_state_create();
    program->state->vert = vert_src;
    program->state->frag = frag_src;


    return program;
}

void vx_program_set_draw_array(vx_program_t * program, int count, int type)
{
    assert(program->state->draw_type == -1); // Enforce only calling this once, for now

    program->state->draw_type = type;
    program->state->draw_count = count;
}


void vx_program_set_vertex_attrib(vx_program_t * program, char * name, vx_resc_t * attrib, int dim)
{

    _vertex_attrib_t * va = malloc(sizeof(_vertex_attrib_t));
    va->vr = attrib;
    va->dim = dim;
    va->name = strdup(name);

    char * old_key = NULL;
    _vertex_attrib_t * old_value = NULL;

    vhash_put(program->state->attribMap,va->name, va, &old_key, &old_value);

    assert(old_key == NULL);
}
