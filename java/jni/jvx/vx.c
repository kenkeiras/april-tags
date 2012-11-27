#define GL_GLEXT_PROTOTYPES

#include "vx.h"
#include <stdio.h>
#include <stdlib.h>
#include <malloc.h>
#include <assert.h>
#include <GL/glew.h>
#include <GL/gl.h>
#include <GL/glx.h>

#include "vx_codes.h"
#include "glcontext.h"

#include "lphash.h"
#include "vhash.h"
#include "lihash.h"
#include "larray.h"
#include "sort_util.h"

#include "vx_resc.h"

static glcontext_t *glc;

typedef struct vx vx_t;


struct vx
{
    gl_fbo_t *fbo;
    int fbo_width, fbo_height;

    lphash_t * resource_map; // holds vx_resc_t

    lphash_t * program_map; // holds gl_prog_resc_t
    lihash_t * vbo_map;

    lihash_t * texture_map;

    vhash_t * buffer_map; // holds vx_buffer_t

    larray_t * dealloc_ids;

    float system_pm[16];

};


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


static vx_t state;
static int verbose = 0;


typedef struct vx_buffer vx_buffer_t;
struct vx_buffer {
    char * name; // used as key in the hash map
    int draw_order;
    vx_code_input_stream_t * codes;
};

static int buffer_compare(const void * a, const void * b)
{
    return ((vx_buffer_t *) a)->draw_order - ((vx_buffer_t *)b)->draw_order;
}

static void checkVersions()
{
	if (glewIsSupported("GL_VERSION_2_0")) {
		if (verbose) printf("Ready for OpenGL 2.0\n");
	}else {
		if (verbose) printf("OpenGL 2.0 not supported\n");
		exit(1);
	}

    const GLubyte *glslVersion =
        glGetString( GL_SHADING_LANGUAGE_VERSION );
    if (verbose) printf("GLSL version %s\n",glslVersion);
}

int vx_initialize()
{
    if (verbose) printf("Creating GL context\n");
    glc = glcontext_X11_create();
    glewInit(); // Call this after GL context created XXX How sure are we that we need this?
    checkVersions();
    return 0;
}

void vx_create()
{
    state.fbo = NULL; // uninitialized

    state.resource_map = lphash_create();

    state.program_map = lphash_create();
    state.vbo_map = lihash_create();
    state.texture_map = lihash_create();

    state.buffer_map = vhash_create(vhash_str_hash, vhash_str_equals);


    state.dealloc_ids = larray_create();

    // Initialize to the identity
    for (int i = 0; i < 4; i++)
        for (int j = 0; j < 4; j++)
            state.system_pm[i*4 + j] = (i == j ? 1.0f : 0.0f);

}

void vx_process_deallocations()
{
    if (verbose) printf("Dealloc %d ids:\n   ", state.dealloc_ids->size);

    for (int i =0; i < state.dealloc_ids->size; i++) {
        uint64_t guid = state.dealloc_ids->get(state.dealloc_ids, i);
        vx_resc_t * vr = lphash_remove(state.resource_map, guid).value;

        if (verbose) printf("%ld,",guid);
       // There may also be a program, or a vbo or texture for each guid
        int vbo_success = 0;
        lihash_pair_t vbo_pair = lihash_remove(state.vbo_map, guid, &vbo_success);
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
        lihash_pair_t tex_pair = lihash_remove(state.texture_map, guid, &tex_success);
        if (tex_success) {
            // Tell open GL to deallocate this texture
            glDeleteTextures(1, &tex_pair.value);
            if (verbose) printf(" Deleted TEX %d \n", tex_pair.value);
        }

        gl_prog_resc_t * prog = lphash_remove(state.program_map, guid).value;
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

    state.dealloc_ids->clear(state.dealloc_ids);
    if (verbose) printf("\n");
}

int vx_update_codes(char * name, int draw_order, vx_code_input_stream_t * codes)
{


    vx_buffer_t * buf = malloc(sizeof(vx_buffer_t));
    buf->name = strdup(name);
    buf->draw_order = draw_order;
    buf->codes = codes;

    if (verbose) printf("Updating codes buffer: %s codes->len %d codes->pos %d\n", buf->name, buf->codes->len, buf->codes->pos);

    char * prev_key = NULL; // ignore this, since the key is stored in the struct
    vx_buffer_t * prev_value = NULL;
    vhash_put(state.buffer_map, buf->name, buf, &prev_key, &prev_value);

    if (prev_value != NULL) {
        vx_code_input_stream_destroy(prev_value->codes);
        free(prev_value->name);
        free(prev_value);
    }

    return 0;
}

int vx_update_resources(int nresc, vx_resc_t ** resources)
{
    if (verbose) printf("Updating %d resources:\n", nresc);
    if (verbose) printf("  ");
    for (int i = 0; i < nresc; i++) {
        vx_resc_t *vr = resources[i];
        if (verbose) printf("%ld,",vr->id);
        vx_resc_t * old_vr = lphash_get(state.resource_map, vr->id);


        if (old_vr == NULL) {
            lphash_put(state.resource_map, vr->id, vr);

        } else {
            // Check to see if this was previously flagged for deletion.
            // If so, unmark for deletion

            int found_idx = -1;
            int found = 0;
            for (int i = 0; i < state.dealloc_ids->size; i++) {
                uint64_t del_guid = state.dealloc_ids->get(state.dealloc_ids, i);
                if (del_guid == vr->id) {
                    found_idx = i;
                    found++;
                }
            }

            state.dealloc_ids->remove(state.dealloc_ids, found_idx);
            assert(found <= 1);

            if (found == 0)
                printf("WRN: ID collision, 0x%lx resource already exists\n", vr->id);
            vx_resc_destroy(vr);
        }
    }
    if (verbose) printf("\n");
    return 0;
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
static GLuint vx_buffer_allocate(GLenum target, vx_resc_t *vr)
{
    GLuint vbo_id;
    glGenBuffers(1, &vbo_id);
    glBindBuffer(target, vbo_id);
    glBufferData(target, vr->count * vr->fieldwidth, vr->res, GL_STATIC_DRAW);
    if (verbose) printf("      Allocated VBO %d for guid %ld\n", vbo_id, vr->id);

    lihash_put(state.vbo_map, vr->id, vbo_id);

    return vbo_id;
}

static void vx_validate_program(GLint prog_id, char * stage_description)
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
int vx_render_program(vx_code_input_stream_t * codes)
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
        gl_prog_resc_t * prog = lphash_get(state.program_map, vertId);
        if (prog == NULL) {
            prog = calloc(sizeof(gl_prog_resc_t), 1);
            // Allocate a program if we haven't made it yet
            prog->vert_id = glCreateShader(GL_VERTEX_SHADER);
            prog->frag_id = glCreateShader(GL_FRAGMENT_SHADER);

            vx_resc_t * vertResc = lphash_get(state.resource_map, vertId);
            vx_resc_t * fragResc = lphash_get(state.resource_map, fragId);

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

            lphash_put(state.program_map, vertId, prog);

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
        vx_validate_program(prog_id, "Post-link");

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
        mult44(state.system_pm, (float *)userM, PM); //XXX

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
        vx_resc_t * vr  = lphash_get(state.resource_map, attribId);
        assert(vr != NULL);

        int success = 0;
        GLuint vbo_id = lihash_get(state.vbo_map, attribId, &success);

        // lazily create VBOs
        if (success)
            glBindBuffer(GL_ARRAY_BUFFER, vbo_id);
        else
            vbo_id = vx_buffer_allocate(GL_ARRAY_BUFFER, vr);

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
        vx_resc_t * vr  = lphash_get(state.resource_map, texId);
        assert(vr != NULL);

        uint32_t width = codes->read_uint32(codes);
        uint32_t height = codes->read_uint32(codes);
        uint32_t format = codes->read_uint32(codes);

        int success = 0;
        GLuint tex_id = lihash_get(state.texture_map, texId, &success);

        if (success)
            glBindTexture(GL_TEXTURE_2D, tex_id);
        else {
            glEnable(GL_TEXTURE_2D);
            glGenTextures(1, &tex_id);

            glBindTexture(GL_TEXTURE_2D, tex_id);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);// XXX Read from codes?

            glTexImage2D(GL_TEXTURE_2D, 0, format, width, height, 0, format, GL_UNSIGNED_BYTE, vr->res);

            if (verbose) printf("Allocated TEX %d for guid %ld\n", tex_id, vr->id);
            lihash_put(state.texture_map, vr->id, tex_id);
        }

        int attrTexI = glGetUniformLocation(prog_id, name);
        glActiveTexture(GL_TEXTURE0 + i);
        glUniform1i(attrTexI, i); // Bind the uniform to TEXTUREi
    }

    if (validateProgram)
        vx_validate_program(prog_id, "Post-binding");

    // Step 5: Rendering
    uint32_t arrayOp = codes->read_uint32(codes);
    assert(arrayOp == OP_ELEMENT_ARRAY ||  arrayOp == OP_DRAW_ARRAY);
    if (arrayOp == OP_ELEMENT_ARRAY)
    {
        uint64_t elementId = codes->read_uint64(codes);
        uint32_t elementType = codes->read_uint32(codes);

        // This should never fail!
        vx_resc_t * vr  = lphash_get(state.resource_map, elementId);
        assert(vr != NULL);

        int success = 0;
        GLuint vbo_id = lihash_get(state.vbo_map, elementId, &success);

        if (success)
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vbo_id);
        else
            vbo_id = vx_buffer_allocate(GL_ELEMENT_ARRAY_BUFFER, vr);

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


// NOTE: Thread safety must be guaranteed externally
int vx_render_read(int width, int height, uint8_t *out_buf)
{
    // Check whether we have a FBO of the correct size
    if (state.fbo == NULL || state.fbo_width != width || state.fbo_height != height) {
        if(state.fbo != NULL)
            gl_fbo_destroy(state.fbo);

        state.fbo = gl_fbo_create(glc, width, height);
        state.fbo_width = width;
        state.fbo_height = height;
        if (verbose) printf("Allocated FBO of dimension %d %d\n",state.fbo_width, state.fbo_height);
    }

    // Deallocate any resources flagged for deletion
    vx_process_deallocations();

    glClear (GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    glViewport(0,0,width,height);

    // debug: print stats
    if (verbose) printf(" n resc %d, n vbos %d, n programs %d n tex %d\n",
           state.resource_map->size,state.vbo_map->size,
           state.program_map->size, state.texture_map->size);

    // For each buffer, process all the programs

    varray_t * buffers = vhash_values(state.buffer_map);
    varray_sort(buffers, buffer_compare);

    for (int i = 0; i < varray_size(buffers); i++) {
        vx_buffer_t * buffer = varray_get(buffers, i);

        buffer->codes->reset(buffer->codes);

        if (verbose) printf("  Rendering buffer: %s with order %d codes->len %d codes->pos %d\n",
                            buffer->name, buffer->draw_order, buffer->codes->len, buffer->codes->pos);

        while (!vx_render_program(buffer->codes));
    }

    glPixelStorei(GL_PACK_ALIGNMENT, 1);
    glReadPixels(0, 0, width, height, GL_BGR, GL_UNSIGNED_BYTE, out_buf);
    return 0;
}

void vx_deallocate_resources(uint64_t * guids, int nguids)
{
    // Add the resources, flag them for deletion later
    // XXX We don't currently handle duplicates already in the list.

    if (verbose) printf("Marking for deletion %d ids:\n   ", nguids);
    for (int i =0; i < nguids; i++) {
        state.dealloc_ids->add(state.dealloc_ids, guids[i]);

        if (verbose) printf("%ld,", guids[i]);
    }
    if (verbose) printf("\n");
}




int vx_set_system_pm_mat(float * pm)
{
    // copy the specified matrix into state
    for (int i = 0; i < 16; i++) {
        state.system_pm[i] = pm[i];
    }
    return 0;
}
