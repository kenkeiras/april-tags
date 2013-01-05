#include "vx_program.h"

#include <stdlib.h>
#include <assert.h>
#include <stdio.h>

#include "vx_codes.h"
#include "vhash.h"
#include <GL/gl.h>
struct vx_program_state
{
    vx_resc_t * vert;
    vx_resc_t * frag;


    vhash_t * attribMap; //<char*, _vertex_attrib_t> and many more

    // Uniforms have memory allocated by this object, stored in these maps
    // which must be freed when this object is destroyed
    vhash_t * uniform4fvMap; //<char*, float *> 4D vector

    //Textures
    vhash_t * texMap; // <char*, _texinfo_t>

    int draw_type;
    int draw_count; // if draw_array
    vx_resc_t * indices; // if element_array
};


typedef struct _vertex_attrib _vertex_attrib_t;
struct _vertex_attrib
{
    vx_resc_t * vr;
    int dim; // how many 'types' per vertex. e.g. 3 for xyz data
    char * name;
};

typedef struct _texinfo _texinfo_t;
struct _texinfo
{
    vx_resc_t * vr;
    char * name;
    int width, height, format;
};


// XXX thread safety, in access, AND initialization....
static char * shader_dir = NULL;
static vhash_t * shader_store = NULL; // Stores _shader_pair each shader name

typedef struct {
    vx_resc_t * vert;
    vx_resc_t * frag;
} _shader_pair_t;

void vx_program_library_init()
{
    assert(shader_dir == NULL && shader_store == NULL);

    shader_dir = malloc(1024);
    char * home_dir = getenv("HOME");
    sprintf(shader_dir, "%s/april/java/shaders",home_dir);
    shader_store = vhash_create(vhash_str_hash, vhash_str_equals);
}

static void shader_pair_destroy(_shader_pair_t * pair)
{
    vx_resc_dec_destroy(pair->vert);
    vx_resc_dec_destroy(pair->frag);
    free(pair);
}

void vx_program_library_destroy()
{
    assert(shader_dir != NULL && shader_store != NULL);
    free(shader_dir);

    vhash_map2(shader_store, free, shader_pair_destroy);
    vhash_destroy(shader_store);
}


vx_program_t * vx_program_load_library(char * name)
{
    assert(shader_dir != NULL && shader_store != NULL);

    _shader_pair_t * pair = vhash_get(shader_store, name);

    if (pair == NULL) {
        char frag_file[1024];
        char vert_file[1024];

        sprintf(frag_file, "%s/%s.frag", shader_dir, name);
        sprintf(vert_file, "%s/%s.vert", shader_dir, name);

        printf("Loading program from %s and %s\n", frag_file, vert_file);

        pair = calloc(1, sizeof(_shader_pair_t));
        pair->vert = vx_resc_load(vert_file);
        pair->frag = vx_resc_load(frag_file);

        vx_resc_inc_ref(pair->vert); // Manually hold onto a reference
        vx_resc_inc_ref(pair->frag);

        vhash_put(shader_store, strdup(name), pair, NULL, NULL);
    }

    return vx_program_create(pair->vert, pair->frag);
}


static vx_program_state_t * vx_program_state_create()
{
    vx_program_state_t * state = malloc(sizeof(vx_program_state_t));
    state->attribMap = vhash_create(vhash_str_hash, vhash_str_equals);
    state->uniform4fvMap = vhash_create(vhash_str_hash, vhash_str_equals);
    state->texMap = vhash_create(vhash_str_hash, vhash_str_equals);

    state->draw_type = -1;
    state->draw_count = -1;
    state->indices = NULL;

    return state;
}

static void _vertex_attrib_destroy(_vertex_attrib_t * attrib)
{
    vx_resc_dec_destroy(attrib->vr);
    free(attrib->name);
    free(attrib);

}

static void _texinfo_destroy(_texinfo_t * tex)
{
    vx_resc_dec_destroy(tex->vr);
    free(tex->name);
    free(tex);
}


static void vx_program_state_destroy(vx_program_state_t * state)
{
    // decrement references to all vx_resources, then call destroy

    // direct references
    vx_resc_dec_destroy(state->vert);
    vx_resc_dec_destroy(state->frag);

    if (state->indices != NULL)
        vx_resc_dec_destroy(state->indices);

    // maps:
    vhash_map2(state->attribMap, NULL, &_vertex_attrib_destroy); // <char*, _vertex_attrib_t>
    vhash_map2(state->uniform4fvMap, &free, &free); // <char*, float*>
    vhash_map2(state->texMap, NULL, &_texinfo_destroy); // <char*, _texinfo_t>

    // note, for _vertex_attrib_t and _texinfo_t, the 'char* name' is
    // stored in the associated struct, so it does not need to be freed using
    // the map2 call, hence we pass NULL.

    vhash_destroy(state->attribMap);
    vhash_destroy(state->uniform4fvMap);
    vhash_destroy(state->texMap);

    // Would also need to decrement any reference counts of sub vx_objects...
    free(state);
}

static void vx_program_destroy(vx_object_t * vo)
{
    vx_program_t * prog = (vx_program_t*)vo->impl;

    vx_program_state_destroy(prog->state);

    free(vo);
    free(prog);
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
    codes->write_uint32(codes, vhash_size(state->uniform4fvMap));
    {
        vhash_iterator_t itr;
        vhash_iterator_init(state->uniform4fvMap, &itr);
        char * key = NULL;
        float * value = NULL;

        while(vhash_iterator_next(&itr, &key, &value)) {
            codes->write_uint32(codes, OP_UNIFORM_VECTOR_FV);
            codes->write_str(codes, key);
            codes->write_uint32(codes, 4);
            codes->write_uint32(codes, 1); // Count, just support for sending one at a time
            for (int i = 0; i < 4; i++)
                codes->write_float(codes, value[i]);
        }
    }

    codes->write_uint32(codes, OP_TEXTURE_COUNT);
    codes->write_uint32(codes, vhash_size(state->texMap));
    {
        vhash_iterator_t itr;
        vhash_iterator_init(state->texMap, &itr);

        char * key = NULL;
        _texinfo_t * value = NULL;
        while(vhash_iterator_next(&itr, &key, &value)) {
            codes->write_uint32(codes, OP_TEXTURE);
            codes->write_str(codes,  value->name);
            codes->write_uint64(codes, value->vr->id);

            codes->write_uint32(codes, value->width);
            codes->write_uint32(codes, value->height);
            codes->write_uint32(codes, value->format);

            lphash_put(resources, value->vr->id, value->vr, NULL); //XXX Existing values?
        }
    }


    // bind drawing instructions
    if (state->indices != NULL) {
        // Element array
        codes->write_uint32(codes, OP_ELEMENT_ARRAY);
        codes->write_uint64(codes, state->indices->id);
        codes->write_uint32(codes, state->draw_type);

        lphash_put(resources, state->indices->id, state->indices, NULL);
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
    vx_object_t * obj = calloc(1,sizeof(vx_object_t));
    obj->append = vx_program_append;
    obj->impl = program;
    obj->destroy = vx_program_destroy;

    program->super = obj;
    program->state = vx_program_state_create();
    program->state->vert = vert_src;
    program->state->frag = frag_src;

    vx_resc_inc_ref(program->state->vert);
    vx_resc_inc_ref(program->state->frag);

    return program;
}

void vx_program_set_draw_array(vx_program_t * program, int count, int type)
{
    assert(program->state->draw_type == -1); // Enforce only calling this once, for now

    program->state->draw_type = type;
    program->state->draw_count = count;
}

void vx_program_set_element_array(vx_program_t * program, vx_resc_t * indices, int type)
{
    assert(program->state->draw_type == -1); // Enforce only calling this once, for now
    assert(indices->type == GL_UNSIGNED_INT);
    program->state->draw_type = type;
    program->state->indices = indices;
    vx_resc_inc_ref(program->state->indices);
}


void vx_program_set_vertex_attrib(vx_program_t * program, char * name, vx_resc_t * attrib, int dim)
{
    vx_resc_inc_ref(attrib);

    _vertex_attrib_t * va = malloc(sizeof(_vertex_attrib_t));
    va->vr = attrib;
    va->dim = dim;
    va->name = strdup(name);

    char * old_key = NULL;
    _vertex_attrib_t * old_value = NULL;

    vhash_put(program->state->attribMap,va->name, va, &old_key, &old_value);

    assert(old_key == NULL);
}


void vx_program_set_uniform4fv(vx_program_t * program, char * name, float * vec4)
{
    char * local_name = strdup(name);
    float * local_vec4 = malloc(sizeof(float)*4);
    memcpy(local_vec4, vec4, sizeof(float)*4);

    char * old_name = NULL;
    float * old_vec4 = NULL;
    vhash_put(program->state->uniform4fvMap,local_name,local_vec4, &old_name, &old_vec4);

    // If there's an old value, free them
    if (old_name) free(old_name);
    if (old_vec4) free(old_vec4);
}


void vx_program_set_texture(vx_program_t * program, char * name, vx_resc_t * vr, int width, int height, int format)
{
    vx_resc_inc_ref(vr);

    _texinfo_t * tinfo = malloc(sizeof(_texinfo_t));
    tinfo->name = strdup(name);
    tinfo->vr = vr;
    tinfo->width = width;
    tinfo->height = height;
    tinfo->format = format;

    char * old_key = NULL;
    _vertex_attrib_t * old_value = NULL;
    vhash_put(program->state->texMap, tinfo->name, tinfo, old_key, old_value);
    assert(old_key == NULL); // For now, don't support over writing existing values
}
