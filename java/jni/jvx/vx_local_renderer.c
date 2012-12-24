#define GL_GLEXT_PROTOTYPES

#include <stdio.h>
#include <stdlib.h>
#include <malloc.h>
#include <assert.h>
#include <GL/glew.h>
#include <GL/gl.h>
#include <GL/glx.h>
#include <pthread.h>

#include "glcontext.h"

#include "lphash.h"
#include "vhash.h"
#include "lihash.h"
#include "larray.h"
#include "sort_util.h"

#include "vx_renderer.h"
#include "vx_local_renderer.h"
#include "vx_codes.h"
#include "vx_resc.h"
#include "vx_resc_mgr.h"

static glcontext_t *glc;

struct vx_local_state
{
    // current state of the fbo for this canvas
    gl_fbo_t *fbo;
    int fbo_width, fbo_height;

    lphash_t * resource_map; // holds vx_resc_t
    lphash_t * program_map; // holds gl_prog_resc_t
    larray_t * dealloc_ids; // resources that need to be deleted

    // holds info about vbo and textures which have been allocated with GL
    lihash_t * vbo_map;
    lihash_t * texture_map;

    vhash_t * layer_map; // <int, vx_layer_info_t>
    vhash_t * world_map; // <int, vx_world_info_t>

    vx_resc_mgr_t * mgr;

    pthread_mutex_t mutex; // this mutex must be locked before any state is modified or accessed
};

#define IMPL_TYPE 0x23847182;

// Resource management for gl program and associated shaders:
// When the associated guid for the vertex shader is dealloacted (vx_resc_t)
// then we also need to free the associated GL resources listed in this struct
// (Note that the vx_resc_t corresponding to the fragment shader also needs to be deallocated
// but that the gl_prog_resc_t is indexed in the hashmap by the
// vertex_shader guid, so this struct (and associated GL objects) are
// only freed when the vertex shader is deallocated)
typedef struct gl_prog_resc gl_prog_resc_t;
struct gl_prog_resc {
    GLuint prog_id; // program id
    GLuint vert_id; // associated vertex_shader
    GLuint frag_id; // associated fragment shader
};


/* static vx_t state; */
static int verbose = 1 ;


typedef struct vx_buffer_info vx_buffer_info_t;
struct vx_buffer_info {
    char * name; // used as key in the hash map
    int draw_order;
    vx_code_input_stream_t * codes;
};


typedef struct vx_layer_info vx_layer_info_t;
struct vx_layer_info {
    int layerID;
    int worldID;
    int draw_order;
    float viewport_rel[4];
    float layer_pm[16];
};

typedef struct vx_world_info vx_world_info_t;
struct vx_world_info {
    int worldID;
    vhash_t * buffer_map; // holds vx_buffer_info_t
};


typedef struct vx_render_task vx_render_task_t;
struct vx_render_task
{
    vx_local_renderer_t * lrend;
    int width, height;
    uint8_t * out_buf;
    pthread_cond_t cond; // signal when job is done
    pthread_mutex_t mutex;
};

struct gl_thread_info {
    pthread_mutex_t  mutex; // lock to access * tasks list
    pthread_cond_t  cond; // signal to notify when new element appears in *tasks
    pthread_t  thread;

    varray_t *  tasks;
};

static struct gl_thread_info gl_thread;

static int buffer_compare(const void * a, const void * b)
{
    return ((vx_buffer_info_t *) a)->draw_order - ((vx_buffer_info_t *)b)->draw_order;
}

static void checkVersions()
{
	if (glewIsSupported("GL_VERSION_2_0")) {
		if (verbose) printf("Ready for OpenGL 2.0\n");
	}else {
		printf("ERR: OpenGL 2.0 not supported\n");
		exit(1);
	}

    const GLubyte *glslVersion =
        glGetString( GL_SHADING_LANGUAGE_VERSION );
    if (verbose) printf("GLSL version %s\n",glslVersion);
}

// foward declaration
void* gl_thread_run();

void gl_init()
{
    if (verbose) printf("Creating GL context\n");
    glc = glcontext_X11_create();
    glewInit(); // Call this after GL context created XXX How sure are we that we need this?
    checkVersions();
}

//XXXX how do we handle this? Depends on whether you want local rendering or not.
int vx_local_initialize()
{

    // start gl_thread
    gl_thread.tasks = varray_create();
    pthread_mutex_init(&gl_thread.mutex, NULL);
    pthread_cond_init(&gl_thread.cond, NULL);
    pthread_create(&gl_thread.thread, NULL, gl_thread_run, NULL);

    return 0;
}

static void vx_local_state_destroy(vx_local_state_t * state)
{
    assert(0); // Need to implement, wait until design gets more finalized
}


static vx_local_state_t * vx_local_state_create()
{
    vx_local_state_t * state = malloc(sizeof(vx_local_state_t));

    state->fbo = NULL; // uninitialized

    state->resource_map = lphash_create();

    state->program_map = lphash_create();
    state->vbo_map = lihash_create();
    state->texture_map = lihash_create();

    state->dealloc_ids = larray_create();

    state->world_map = vhash_create(vhash_uint32_hash, vhash_uint32_equals);
    state->layer_map = vhash_create(vhash_uint32_hash, vhash_uint32_equals);

    state->mgr = NULL; // delayed

    pthread_mutex_init(&state->mutex, NULL);

    return state;
}


static void process_deallocations(vx_local_state_t * state)
{
    if (verbose) printf("Dealloc %d ids:\n   ", state->dealloc_ids->size);

    for (int i =0; i < state->dealloc_ids->size; i++) {
        uint64_t guid = state->dealloc_ids->get(state->dealloc_ids, i);

        vx_resc_t * vr = NULL;
        lphash_remove(state->resource_map, guid, &vr);
        assert(vr != NULL);

        if (verbose) printf("%ld,",guid);
       // There may also be a program, or a vbo or texture for each guid
        int vbo_success = 0;
        lihash_pair_t vbo_pair = lihash_remove(state->vbo_map, guid, &vbo_success);
        if (vbo_success) {
            // Tell open GL to deallocate this VBO
            glDeleteBuffers(1, &vbo_pair.value);
            if (verbose) printf(" Deleted VBO %d \n", vbo_pair.value);
        }

        // There is always a resource for each guid.
        if (verbose) printf("Deallocating guid %ld:\n",guid);
        assert(guid == vr->id);
        if (vr != NULL) {
            if (verbose) printf("Deallocating resource GUID=%ld\n", vr->id);
            free(vr->res);
            free(vr);
        } else {
            printf("WRN!: Invalid request. Resource %ld does not exist", guid);
        }


        int tex_success = 0;
        lihash_pair_t tex_pair = lihash_remove(state->texture_map, guid, &tex_success);
        if (tex_success) {
            // Tell open GL to deallocate this texture
            glDeleteTextures(1, &tex_pair.value);
            if (verbose) printf(" Deleted TEX %d \n", tex_pair.value);
        }

        gl_prog_resc_t * prog = NULL;
        lphash_remove(state->program_map, guid, &prog);
        assert(prog!=NULL);

        if (prog != NULL) {
            glDetachShader(prog->prog_id,prog->vert_id);
            glDeleteShader(prog->vert_id);
            glDetachShader(prog->prog_id,prog->frag_id);
            glDeleteShader(prog->frag_id);
            glDeleteProgram(prog->prog_id);

            if (verbose) printf("  Freed program %d vert %d and frag %d\n",
                                prog->prog_id, prog->vert_id, prog->frag_id);
            free(prog);
        }
    }

    state->dealloc_ids->clear(state->dealloc_ids);
    if (verbose) printf("\n");
}

static void vx_local_update_layer(vx_local_renderer_t * lrend, int layerID, int worldID, int draw_order, float viewport_rel[4])
{
    vx_layer_info_t * layer = vhash_get(lrend->state->layer_map, (void*)layerID);
    if (layer == NULL) { // Allocate a new layer  -- XXX no way to dealloc
        layer = malloc(sizeof(vx_layer_info_t));
        layer->layerID = layerID;
        layer->worldID = worldID;

        if (verbose) printf("Initializing layer %d\n", layerID);
        // Initialize projection*model matrix to the identity
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++)
                layer->layer_pm[i*4 + j] = (i == j ? 1.0f : 0.0f);

        int old_key = 0;
        vx_layer_info_t * old_value = NULL;
        vhash_put(lrend->state->layer_map, (void *) layerID, layer, &old_key, &old_value);
        assert(old_value == NULL);
    }
    // changing worlds is not allowed, for now
    assert(layerID == layer->layerID);
    assert(worldID == layer->worldID);

    // dynamic content: order and viewport
    layer->draw_order = draw_order;
    memcpy(layer->viewport_rel, viewport_rel, 4*4);
    if (verbose) printf("New viewport for id %d is [%f, %f, %f, %f]\n",
                        layerID, layer->viewport_rel[0],layer->viewport_rel[1],layer->viewport_rel[2],layer->viewport_rel[3]);
}

static void vx_local_update_buffer(vx_local_renderer_t * lrend, int worldID, char * name, int draw_order, uint8_t * data, int datalen)
{
    vx_code_input_stream_t * codes = vx_code_input_stream_create(data, datalen); // copies data

    vx_world_info_t * world = vhash_get(lrend->state->world_map, (void *)worldID);
    if (world == NULL) { // Allocate a new world -- XXX no way to dealloc
        world = malloc(sizeof(vx_world_info_t));
        world->worldID = worldID;
        world->buffer_map = vhash_create(vhash_str_hash, vhash_str_equals);

        int old_key = 0;
        vx_world_info_t * old_value = NULL;
        vhash_put(lrend->state->world_map, (void*)worldID, world, &old_key, &old_value);
        assert(old_value == NULL);
    }

    vx_buffer_info_t * buf = malloc(sizeof(vx_buffer_info_t));
    buf->name = strdup(name);
    buf->draw_order = draw_order;
    buf->codes = codes;

    if (verbose) printf("Updating codes buffer: world ID %d %s codes->len %d codes->pos %d\n", worldID, buf->name, buf->codes->len, buf->codes->pos);

    char * prev_key = NULL; // ignore this, since the key is stored in the struct
    vx_buffer_info_t * prev_value = NULL;
    vhash_put(world->buffer_map, buf->name, buf, &prev_key, &prev_value);

    if (prev_value != NULL) {
        vx_code_input_stream_destroy(prev_value->codes);
        free(prev_value->name);
        free(prev_value);
    }
}

// XXX Need to setup a memory management scheme for vx_resc_t that are passed in. Who is responsible for freeing them? Should we just always make a copy?
static void vx_local_add_resources_direct(vx_local_renderer_t * lrend, lphash_t * resources)
{
    if (verbose) printf("Updating %d resources:\n", lphash_size(resources));
    if (verbose) printf("  ");


    lphash_iterator_t itr;
    lphash_iterator_init(resources, &itr);
    uint64_t id = -1;
    vx_resc_t * vr = NULL;
    while(lphash_iterator_next(&itr, &id, &vr)) {
        vx_resc_t * old_vr = lphash_get(lrend->state->resource_map, vr->id);

        if (old_vr == NULL) {
            lphash_put(lrend->state->resource_map, vr->id, vr, NULL);
        } else {
            // Check to see if this was previously flagged for deletion.
            // If so, unmark for deletion

            int found_idx = -1;
            int found = 0;
            for (int i = 0; i < lrend->state->dealloc_ids->size; i++) {
                uint64_t del_guid = lrend->state->dealloc_ids->get(lrend->state->dealloc_ids, i);
                if (del_guid == vr->id) {
                    found_idx = i;
                    found++;
                }
            }

            lrend->state->dealloc_ids->remove(lrend->state->dealloc_ids, found_idx);
            assert(found <= 1);

            if (found == 0)
                printf("WRN: ID collision, 0x%lx resource already exists\n", vr->id);

            if (vr != old_vr) // XXX Only delete this if it won't cause trouble later
                vx_resc_destroy(vr);
        }
    }
    if (verbose) printf("\n");
}

static void print44(float mat[16])
{
    int m = 4, n = 4;
    for (int i = 0; i < m; i++) {
        for (int j = 0; j < n; j++)
            printf("% f\t", mat[i*n+j]);
        printf("\n");
    }
}

static void mult44(float * A, float * B, float * C)
{
    for (int i = 0; i < 4; i++) {
        for (int j = 0; j < 4; j++) {
            float acc = 0.0f;
            for (int k = 0; k < 4; k++)
                acc += A[i*4 + k] * B[k*4 + j];
            C[i*4 +j] = acc;
        }
    }
}

// Allocates new VBO, and stores in hash table, results in a bound VBO
static GLuint vbo_allocate(vx_local_state_t * state, GLenum target, vx_resc_t *vr)
{
    GLuint vbo_id;
    glGenBuffers(1, &vbo_id);
    glBindBuffer(target, vbo_id);
    glBufferData(target, vr->count * vr->fieldwidth, vr->res, GL_STATIC_DRAW);
    if (verbose) printf("      Allocated VBO %d for guid %ld\n", vbo_id, vr->id);

    lihash_put(state->vbo_map, vr->id, vbo_id);

    return vbo_id;
}

static void validate_program(GLint prog_id, char * stage_description)
{
    char output[65535];

    GLint len = 0;
    glGetProgramInfoLog(prog_id, 65535, &len, output);
    if (len != 0)
        printf("%s len = %d:\n%s\n", stage_description, len, output);

}

// This function does the heavy lifting for rendering a data + program pair
// Which program, and data to use are specified in the codes
// input_stream
// This breaks down into the following steps:
// 1) Find the glProgram. If it doesn't exist, create it from the associated
//    vertex and fragment shader string resources
// 2) Find all the VBOs that will bound as vertex attributes. If they
//    don't exist, create them from the specified resources
// 3) Read all the data to be bound as uniforms from the input stream.
// 4) Textures
// 5) Render using either glDrawArrays or glElementArray
//
// Note: After step 1 and 4, the state of the program is queried,
//       and debugging information (if an error occurs) is printed to stdout
int render_program(vx_local_state_t * state, vx_layer_info_t *layer, vx_code_input_stream_t * codes)
{
    if (codes->len == codes->pos) // exhausted the stream
        return 1;
    if (verbose) printf("  Processing program, codes has %d remaining\n",codes->len-codes->pos);

    // STEP 1: find/allocate the glProgram (using vertex shader and fragment shader)
    GLuint prog_id = -1;
    {
        uint32_t programOp = codes->read_uint32(codes);
        assert(programOp == OP_PROGRAM);
        uint64_t vertId = codes->read_uint64(codes);
        uint64_t fragId = codes->read_uint64(codes);

        // Programs can be found by the guid of the vertex shader
        gl_prog_resc_t * prog = lphash_get(state->program_map, vertId);
        if (prog == NULL) {
            prog = calloc(sizeof(gl_prog_resc_t), 1);
            // Allocate a program if we haven't made it yet
            prog->vert_id = glCreateShader(GL_VERTEX_SHADER);
            prog->frag_id = glCreateShader(GL_FRAGMENT_SHADER);

            vx_resc_t * vertResc = lphash_get(state->resource_map, vertId);
            vx_resc_t * fragResc = lphash_get(state->resource_map, fragId);

            // shouldn't fail, if resources are uploaded first
            assert(vertResc != NULL);
            assert(fragResc != NULL);

            const char * vertSource = vertResc->res;
            const char * fragSource = fragResc->res;

            glShaderSource(prog->vert_id, 1, &vertSource, NULL);
            glShaderSource(prog->frag_id, 1, &fragSource, NULL);

            glCompileShader(prog->vert_id);
            glCompileShader(prog->frag_id);

            prog->prog_id = glCreateProgram();

            glAttachShader(prog->prog_id, prog->vert_id);
            glAttachShader(prog->prog_id, prog->frag_id);

            glLinkProgram(prog->prog_id);

            lphash_put(state->program_map, vertId, prog, NULL);

            if (verbose) printf("  Created gl program %d from guid %ld and %ld (gl ids %d and %d)\n",
                   prog->prog_id, vertId, fragId, prog->vert_id, prog->frag_id);
        }
        prog_id = prog->prog_id;
        glUseProgram(prog_id);
    }

    uint32_t validateProgramOp = codes->read_uint32(codes);
    assert(validateProgramOp == OP_VALIDATE_PROGRAM);
    uint32_t validateProgram = codes->read_uint32(codes);
    if (validateProgram)
        validate_program(prog_id, "Post-link");

    // STEP 1.5: Read the user-specified model matrix, and multiply
    // with the system defined projection-model matrix. Then bind to
    // the user-specified uniform name
    {
        uint32_t modelMatrixOp = codes->read_uint32(codes);
        assert(modelMatrixOp == OP_MODEL_MATRIX_44);

        // XXX ugly way to deal with floats
        float userM[16];
        for (int i =0; i < 16; i++)
            userM[i] = codes->read_float(codes);

        uint32_t pmMatrixNameOp = codes->read_uint32(codes);
        assert(pmMatrixNameOp == OP_PM_MAT_NAME);
        char * pmName = codes->read_str(codes);

        float PM[16];
        mult44(layer->layer_pm, (float *)userM, PM); //XXX

        GLint unif_loc = glGetUniformLocation(prog_id, pmName);
        assert(unif_loc >= 0); // Ensure this field exists
        glUniformMatrix4fv(unif_loc, 1 , 1, (GLfloat *)PM);
    }

    // STEP 2: Bind all vertex attributes, backed by VBOs. Carefully
    // record which vertex attributes we enable so we can disable them later
    uint32_t attribCountOp = codes->read_uint32(codes);
    assert(attribCountOp == OP_VERT_ATTRIB_COUNT);
    uint32_t attribCount = codes->read_uint32(codes);
    GLint attribLocs[attribCount];
    for (int i = 0; i < attribCount; i++) {
        uint32_t attribOp = codes->read_uint32(codes);
        assert(attribOp == OP_VERT_ATTRIB);
        uint64_t attribId = codes->read_uint64(codes);
        uint32_t dim = codes->read_uint32(codes);
        char * name = codes->read_str(codes); //Not a copy!

        // This should never fail!
        vx_resc_t * vr  = lphash_get(state->resource_map, attribId);
        assert(vr != NULL);

        int success = 0;
        GLuint vbo_id = lihash_get(state->vbo_map, attribId, &success);

        // lazily create VBOs
        if (success)
            glBindBuffer(GL_ARRAY_BUFFER, vbo_id);
        else
            vbo_id = vbo_allocate(state, GL_ARRAY_BUFFER, vr);

        // Attach to attribute
        GLint attr_loc = glGetAttribLocation(prog_id, name);
        attribLocs[i] = attr_loc;

        glEnableVertexAttribArray(attr_loc);
        glVertexAttribPointer(attr_loc, dim, vr->type, 0, 0, 0);
        assert(vr->type == GL_FLOAT);
    }

    // STEP 3: Send over all data relating to uniforms.
    // There are many data formats, we currently only support a subset
    uint32_t uniCountOp = codes->read_uint32(codes);
    assert(uniCountOp == OP_UNIFORM_COUNT);
    uint32_t uniCount = codes->read_uint32(codes);
    for (int i = 0; i < uniCount; i++) {
        uint32_t uniOp = codes->read_uint32(codes);

        // Functionality common to all uniforms, regardless of type
        char * name = codes->read_str(codes);
        GLint unif_loc = glGetUniformLocation(prog_id, name);
        // Functionality depends on type:
        uint32_t nper = 0; // how many per unit?
        uint32_t count = 0; // how many units?
        uint32_t transpose = 0;
        switch(uniOp) {
            case OP_UNIFORM_MATRIX_FV:
                nper = codes->read_uint32(codes);
                count = codes->read_uint32(codes);
                transpose = codes->read_uint32(codes);
                break;
            case OP_UNIFORM_VECTOR_FV:
                nper = codes->read_uint32(codes);
                count = codes->read_uint32(codes);
                break;
        }

        // The uniform data is stored at the end, so it can be copied
        // into statically allocated array
        int vals[count*nper];
        for (int j = 0; j < nper*count; j++)
            vals[i++] = codes->read_uint32(codes);

        // Finally, once the right amount of data is extracted, ship it with the appropriate method:
        switch(uniOp) {
            case OP_UNIFORM_MATRIX_FV:
                if (nper == 16)
                    glUniformMatrix4fv(unif_loc, count, transpose, (GLfloat *)vals);
                break;
            case OP_UNIFORM_VECTOR_FV:
                if (nper == 4)
                    glUniform4fv(unif_loc, count, (GLfloat *) vals);
                break;
        }
    }

    // Step 4: Bind and upload all textures. We also bind the texture
    // id to the appropriate uniform
    uint32_t texCountOp = codes->read_uint32(codes);
    assert(texCountOp == OP_TEXTURE_COUNT);
    uint32_t texCount = codes->read_uint32(codes);

    if (texCount > 1)
        printf("WRN: Multiple textures not tested\n");

    for (int i = 0; i < texCount; i++) {
        uint32_t texOp = codes->read_uint32(codes);
        assert(texOp == OP_TEXTURE);
        char * name = codes->read_str(codes);
        uint64_t texId = codes->read_uint64(codes);
        // This should never fail!
        vx_resc_t * vr  = lphash_get(state->resource_map, texId);
        assert(vr != NULL);

        uint32_t width = codes->read_uint32(codes);
        uint32_t height = codes->read_uint32(codes);
        uint32_t format = codes->read_uint32(codes);

        int success = 0;
        GLuint tex_id = lihash_get(state->texture_map, texId, &success);

        if (success)
            glBindTexture(GL_TEXTURE_2D, tex_id);
        else {
            glEnable(GL_TEXTURE_2D);
            glGenTextures(1, &tex_id);

            glBindTexture(GL_TEXTURE_2D, tex_id);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);// XXX Read from codes?

            glTexImage2D(GL_TEXTURE_2D, 0, format, width, height, 0, format, GL_UNSIGNED_BYTE, vr->res);

            if (verbose) printf("Allocated TEX %d for guid %ld\n", tex_id, vr->id);
            lihash_put(state->texture_map, vr->id, tex_id);
        }

        int attrTexI = glGetUniformLocation(prog_id, name);
        glActiveTexture(GL_TEXTURE0 + i);
        glUniform1i(attrTexI, i); // Bind the uniform to TEXTUREi
    }

    if (validateProgram)
        validate_program(prog_id, "Post-binding");

    // Step 5: Rendering
    uint32_t arrayOp = codes->read_uint32(codes);
    assert(arrayOp == OP_ELEMENT_ARRAY ||  arrayOp == OP_DRAW_ARRAY);
    if (arrayOp == OP_ELEMENT_ARRAY)
    {
        uint64_t elementId = codes->read_uint64(codes);
        uint32_t elementType = codes->read_uint32(codes);

        // This should never fail!
        vx_resc_t * vr  = lphash_get(state->resource_map, elementId);
        assert(vr != NULL);

        int success = 0;
        GLuint vbo_id = lihash_get(state->vbo_map, elementId, &success);

        if (success)
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vbo_id);
        else
            vbo_id = vbo_allocate(state, GL_ELEMENT_ARRAY_BUFFER, vr);

        glDrawElements(elementType, vr->count, vr->type, NULL);
    } else if (arrayOp == OP_DRAW_ARRAY) {
        uint32_t drawCount = codes->read_uint32(codes);
        uint32_t drawType = codes->read_uint32(codes);

        glDrawArrays(drawType, 0, drawCount);
    }

    // Important: Disable all vertex attribute arrays, or we can contaminate the state
    // for future programs. Might be resolved by switching to VBAs
    for (int i = 0; i < attribCount; i++)
        glDisableVertexAttribArray(attribLocs[i]);

    return 0;
}

static void resize_fbo(vx_local_state_t * state, int width, int height)
{
    // Check whether we have a FBO of the correct size
    if (state->fbo == NULL || state->fbo_width != width || state->fbo_height != height) {
        if(state->fbo != NULL)
            gl_fbo_destroy(state->fbo);

        state->fbo = gl_fbo_create(glc, width, height);
        state->fbo_width = width;
        state->fbo_height = height;
        if (verbose) printf("Allocated FBO of dimension %d %d\n",state->fbo_width, state->fbo_height);
    }
}

// NOTE: Thread safety must be guaranteed externally
static void vx_local_render(vx_local_renderer_t * lrend, int width, int height, uint8_t *out_buf)
{
    // debug: print stats
    if (verbose) printf("n layers %d n resc %d, n vbos %d, n programs %d n tex %d w %d h %d\n",
                        vhash_size(lrend->state->layer_map),
                        lphash_size(lrend->state->resource_map),
                        lrend->state->vbo_map->size,
                        lphash_size(lrend->state->program_map),
                        lrend->state->texture_map->size, width, height);

    resize_fbo(lrend->state, width, height);

    // Deallocate any resources flagged for deletion
    process_deallocations(lrend->state);

    glClear (GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    glViewport(0,0,width,height);



    // We process each layer, change the viewport, and then process each buffer in the associated world:
    varray_t * layers = vhash_values(lrend->state->layer_map);
    //XXX Need to sort

    for (int i = 0; i < varray_size(layers); i++) {
        vx_layer_info_t * layer = varray_get(layers, i);
        vx_world_info_t * world = vhash_get(lrend->state->world_map, (void *)(layer->worldID));
        if (world == NULL){ // haven't uploaded world yet?
            if (verbose) printf("WRN: world %d not populated yet!", layer->worldID);
            continue;
        }
        /* assert(world != NULL); */

        // convert from relative to absolute viewport
        int viewport[] = {(int)(width  * layer->viewport_rel[0]),
                          (int)(height * layer->viewport_rel[1]),
                          (int)(width  * layer->viewport_rel[2]),
                          (int)(height * layer->viewport_rel[3])};

        glScissor(viewport[0],viewport[1],viewport[2],viewport[3]);
        glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
        if (verbose) printf("viewport for layer %d is [%d, %d, %d, %d]\n",
                            layer->layerID, viewport[0],viewport[1],viewport[2],viewport[3]);

        // XXX Background color

        varray_t * buffers = vhash_values(world->buffer_map);
        varray_sort(buffers, buffer_compare);

        for (int i = 0; i < varray_size(buffers); i++) {
            vx_buffer_info_t * buffer = varray_get(buffers, i);

            buffer->codes->reset(buffer->codes);

            if (verbose) printf("  Rendering buffer: %s with order %d codes->len %d codes->pos %d\n",
                                buffer->name, buffer->draw_order, buffer->codes->len, buffer->codes->pos);

            while (!render_program(lrend->state, layer, buffer->codes));
        }

    }


    // For each buffer, process all the programs


    glPixelStorei(GL_PACK_ALIGNMENT, 1);
    glReadPixels(0, 0, width, height, GL_BGR, GL_UNSIGNED_BYTE, out_buf);
}

static void vx_local_remove_resources_direct(vx_local_renderer_t *lrend, lphash_t * resources)
{
    // Add the resources, flag them for deletion later
    // XXX We don't currently handle duplicates already in the list.

    if (verbose) printf("Marking for deletion %d ids:\n", lphash_size(resources));

    lphash_iterator_t itr;
    lphash_iterator_init(resources, &itr);
    uint64_t id = -1;
    vx_resc_t * vr = NULL;
    while(lphash_iterator_next(&itr, &id, &vr)) {
        lrend->state->dealloc_ids->add(lrend->state->dealloc_ids, vr->id);

        if (verbose) printf("%ld,", vr->id);
    }
    if (verbose) printf("\n");
}



static void vx_local_set_layer_pm_matrix(vx_local_renderer_t *lrend, int layerID, float * pm)
{
    vx_layer_info_t * layer = vhash_get(lrend->state->layer_map, (void*)layerID);
    if (layer == NULL) {
        printf("WRN: Layer %d not found when attempting to set pm matrix\n", layerID);
        return;
    }
    memcpy(layer->layer_pm, pm, 16*4);
}

static void vx_local_update_resources_managed(vx_local_renderer_t * lrend, int worldID, char * buffer_name, lphash_t * resources)
{
    vx_resc_mgr_update_resources_managed(lrend->state->mgr, worldID, buffer_name, resources);

    assert(0);
}

static void vx_local_get_canvas_size(vx_local_renderer_t * lrend, int * dim_out)
{
    assert(0);
}

// Wrapper methods for the interface --> cast to vx_local_renderer_t and then call correct function
// Also ensure pthread safety (suffix _ts means "thread-safe")
static void vx_update_resources_managed_ts(vx_renderer_t * rend, int worldID, char * buffer_name, lphash_t * resources)
{
    vx_local_renderer_t * lrend =  (vx_local_renderer_t *)(rend->impl);
    pthread_mutex_lock(&lrend->state->mutex);
    vx_local_update_resources_managed(lrend, worldID, buffer_name, resources);
    pthread_mutex_unlock(&lrend->state->mutex);
}

static void vx_add_resources_direct_ts(vx_renderer_t * rend, lphash_t * resources)
{
    vx_local_renderer_t * lrend =  (vx_local_renderer_t *)(rend->impl);
    pthread_mutex_lock(&lrend->state->mutex);
    vx_local_add_resources_direct(lrend, resources);
    pthread_mutex_unlock(&lrend->state->mutex);
}

static void vx_remove_resources_direct_ts(vx_renderer_t * rend, lphash_t * resources)
{
    vx_local_renderer_t * lrend =  (vx_local_renderer_t *)(rend->impl);
    pthread_mutex_lock(&lrend->state->mutex);
    vx_local_remove_resources_direct(lrend, resources);
    pthread_mutex_unlock(&lrend->state->mutex);
}

static void vx_update_buffer_ts(vx_renderer_t * rend, int worldID, char * buffer_name, int drawOrder, uint8_t * data, int datalen)
{
    vx_local_renderer_t * lrend =  (vx_local_renderer_t *)(rend->impl);
    pthread_mutex_lock(&lrend->state->mutex);
    vx_local_update_buffer(lrend, worldID, buffer_name, drawOrder, data, datalen);
    pthread_mutex_unlock(&lrend->state->mutex);
}

static void vx_update_layer_ts(vx_renderer_t * rend, int layerID, int worldID, int draw_order, float viewport_rel[4])
{
    vx_local_renderer_t * lrend =  (vx_local_renderer_t *)(rend->impl);
    pthread_mutex_lock(&lrend->state->mutex);
    vx_local_update_layer(lrend, layerID, worldID, draw_order, viewport_rel);
    pthread_mutex_unlock(&lrend->state->mutex);
}

static void vx_get_canvas_size_ts(vx_renderer_t * rend, int * dim_out)
{
    vx_local_renderer_t * lrend =  (vx_local_renderer_t *)(rend->impl);
    pthread_mutex_lock(&lrend->state->mutex);
    vx_local_get_canvas_size(lrend, dim_out);
    pthread_mutex_unlock(&lrend->state->mutex);
}

static void vx_destroy_ts(vx_renderer_t * rend)
{
    // XXX Is there a thread safe way to do this?
    vx_local_state_destroy(((vx_local_renderer_t *)(rend->impl))->state);
    free(rend->impl);
    free(rend);
}

// methods specific to vx_local:
static void vx_local_set_layer_pm_matrix_ts(vx_local_renderer_t *lrend, int layerID, float * pm)
{
    pthread_mutex_lock(&lrend->state->mutex);

    vx_local_set_layer_pm_matrix(lrend, layerID, pm);

    pthread_mutex_unlock(&lrend->state->mutex);
}

// push a render task onto the stack
static void vx_local_render_ts(vx_local_renderer_t * lrend, int width, int height, uint8_t *out_buf)
{
    // init
    vx_render_task_t task;
    pthread_cond_init(&task.cond, NULL);
    pthread_mutex_init(&task.mutex, NULL);

    task.lrend = lrend;
    task.width = width;
    task.height = height;
    task.out_buf = out_buf;

    // lock early to ensure that we will be notified about the task being complete.
    pthread_mutex_lock(&task.mutex);

    // push the task, and notify
    pthread_mutex_lock(&gl_thread.mutex);
    varray_add(gl_thread.tasks, &task);
    pthread_cond_signal(&gl_thread.cond);
    pthread_mutex_unlock(&gl_thread.mutex);

    //wait for completion, and unlock
    pthread_cond_wait(&task.cond, &task.mutex);
    pthread_mutex_unlock(&task.mutex);

    // cleanup
    pthread_cond_destroy(&task.cond);
    pthread_mutex_destroy(&task.mutex);
}


void* gl_thread_run()
{
    gl_init(); // Ensure this occurs in the gl thread
    while (1) {
        pthread_mutex_lock(&gl_thread.mutex);
        if (varray_size(gl_thread.tasks) == 0) {
            pthread_cond_wait(&gl_thread.cond, &gl_thread.mutex);
        } else {
            // run a rending task
            vx_render_task_t * task = varray_remove(gl_thread.tasks, 0);

            pthread_mutex_lock(&task->lrend->state->mutex);
            vx_local_render(task->lrend, task->width, task->height, task->out_buf);
            pthread_mutex_unlock(&task->lrend->state->mutex);

            pthread_cond_signal(&task->cond);

        }
        pthread_mutex_unlock(&gl_thread.mutex);
    }
    pthread_exit(NULL);
}

// XXX deal with width and height
vx_local_renderer_t * vx_create_local_renderer(int width, int height)
{
    vx_local_renderer_t * local = malloc(sizeof(vx_local_renderer_t));
    local->super = malloc(sizeof(vx_renderer_t));
    local->super->impl_type = IMPL_TYPE;

    // set all methods
    local->super->update_resources_managed = vx_update_resources_managed_ts;
    local->super->add_resources_direct = vx_add_resources_direct_ts;
    local->super->remove_resources_direct = vx_remove_resources_direct_ts;
    local->super->update_buffer = vx_update_buffer_ts;
    local->super->update_layer = vx_update_layer_ts;
    local->super->get_canvas_size = vx_get_canvas_size_ts;
    local->super->destroy = vx_destroy_ts;

    local->render = vx_local_render_ts;
    local->set_layer_pm_matrix = vx_local_set_layer_pm_matrix_ts;

    // Deal with private storage:
    local->super->impl = local; // ugly circular reference, but allows us to convert classes both ways


    local->state = vx_local_state_create();
    local->state->mgr = vx_resc_mgr_create(local->super);
    return local;
}
