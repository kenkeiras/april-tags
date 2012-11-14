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

#include "vx_resc.h"
#define MAX_FBOS 128


typedef struct vx vx_t;

struct vx
{
    glcontext_t *glc;
    gl_fbo_t *fbos[MAX_FBOS];

    lphash_t * resource_map; // holds vx_resc_t

    lphash_t * program_map; // holds gl_prog_resc_t
    lihash_t * vbo_map;

    lihash_t * texture_map;

    vhash_t * buffer_codes_map;


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


static void checkVersions()
{
	if (glewIsSupported("GL_VERSION_2_0"))
		printf("Ready for OpenGL 2.0\n");
	else {
		printf("OpenGL 2.0 not supported\n");
		exit(1);
	}

    const GLubyte *glslVersion =
        glGetString( GL_SHADING_LANGUAGE_VERSION );
    printf("GLSL version %s\n",glslVersion);
}

int vx_init()
{
    state.glc = glcontext_X11_create();

    glewInit(); // Call this after GL context created XXX How sure are we that we need this?
    checkVersions();


    state.resource_map = lphash_create();

    state.program_map = lphash_create();
    state.vbo_map = lihash_create();
    state.texture_map = lihash_create();

    state.buffer_codes_map = vhash_create(vhash_str_hash, vhash_str_equals);

    return 0;
}

int fbo_create(int width, int height)
{
    gl_fbo_t *fbo = gl_fbo_create(state.glc, width, height);

    if (fbo == NULL)
        return -1;

    // skip id 0...
    for (int i = 1; i < MAX_FBOS; i++) {
        if (state.fbos[i] == NULL) {
            state.fbos[i] = fbo;
            return i;
        }
    }

    return -2;
}

int vx_update_buffer(char * name, vx_code_input_stream_t * codes)
{
    printf("Updating buffer: %s codes->len %d codes->pos %d\n", name, codes->len, codes->pos);

    if (vhash_get(state.buffer_codes_map, name) != NULL) {
        vhash_pair_t prev = vhash_remove(state.buffer_codes_map, name);

        name = prev.key; // Reuse the old key
        vx_code_input_stream_destroy(prev.value);

    } else { // First time we've seen this buffer
        name = strdup(name);
    }

    vhash_put(state.buffer_codes_map, name, codes);
    return 0;
}

int vx_update_resources(int nresc, vx_resc_t ** resources)
{
    for (int i = 0; i < nresc; i++) {
        vx_resc_t *vr = resources[i];

        vx_resc_t * old_vr = lphash_get(state.resource_map, vr->id);

        if (old_vr == NULL) {
            lphash_put(state.resource_map, vr->id, vr);

        } else {
            printf("WRN: ID collision, 0x%lx resource already exists\n", vr->id);
            vx_resc_destroy(vr);
        }
    }
    return 0;
}

// Allocates new VBO, and stores in hash table, results in a bound VBO
static GLuint vx_buffer_allocate(GLenum target, vx_resc_t *vr)
{
    GLuint vbo_id;
    glGenBuffers(1, &vbo_id);
    glBindBuffer(target, vbo_id);
    glBufferData(target, vr->count * vr->fieldwidth, vr->res, GL_STATIC_DRAW);
    printf("Allocated VBO %d for guid %ld\n", vbo_id, vr->id);

    lihash_put(state.vbo_map, vr->id, vbo_id);

    return vbo_id;
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
// 5) Find the VBO with the index data, if it doesn't exist, create it
//    from the specified resource. This triggers the rendering operation
//
// Note: After step 1 and 4, the state of the program is queried,
//       and debugging information (if an error occurs) is printed to stdout
int vx_render_program(vx_code_input_stream_t * codes)
{
    if (codes->len == codes->pos) // exhausted the stream
        return 1;
    printf("Processing program, codes has %d remaining\n",codes->len-codes->pos);

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
        }
        prog_id = prog->prog_id;
        glUseProgram(prog_id);
    }

    if (1) { // Check program status
        char output[65535];

        GLint len = 0;
        glGetProgramInfoLog(prog_id, 65535, &len, output);
        if (len != 0)
            printf("Post-link len = %d:\n%s\n", len, output);
    }

    uint32_t attribCountOp = codes->read_uint32(codes);
    assert(attribCountOp == OP_VERT_ATTRIB_COUNT);
    uint32_t attribCount = codes->read_uint32(codes);

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
        glEnableVertexAttribArray(attr_loc);
        glVertexAttribPointer(attr_loc, dim, vr->type, 0, 0, 0);
        assert(vr->type == GL_FLOAT);
    }

    uint32_t uniCountOp = codes->read_uint32(codes);
    assert(uniCountOp == OP_UNIFORM_COUNT);
    uint32_t uniCount = codes->read_uint32(codes);
    for (int i = 0; i < uniCount; i++) {

        uint32_t uniOp = codes->read_uint32(codes);

        char* name = NULL;
        uint32_t nper = 0; // how many per unit?
        uint32_t count = 0; // how many units?
        uint32_t transpose = 0;

        GLint unif_loc = -1;
        switch(uniOp) {
            case OP_UNIFORM_MATRIX_FV:
                name = codes->read_str(codes);
                unif_loc = glGetUniformLocation(prog_id, name);

                nper = codes->read_uint32(codes);
                count = codes->read_uint32(codes);
                transpose = codes->read_uint32(codes);
                break;
        }

        // The uniform data is stored at the end, so it can be copied
        // into statically allocated array
        int vals[count*nper];
        for (int j = 0; j < nper*count; j++)
            vals[i++] = codes->read_uint32(codes);

        switch(uniOp) {
            case OP_UNIFORM_MATRIX_FV:
                if (nper == 16)
                    glUniformMatrix4fv(unif_loc, count, transpose, (GLfloat *)vals);
                break;
        }
    }

    // Step 4: Textures
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

            printf("Allocated TEX %d for guid %ld\n", tex_id, vr->id);
            lihash_put(state.texture_map, vr->id, tex_id);
        }

        int attrTexI = glGetUniformLocation(prog_id, name);
        glActiveTexture(GL_TEXTURE0 + i);
        glUniform1i(attrTexI, i); // Bind the uniform to TEXTUREi
    }


    if (1) {
        char output[65535];

        glValidateProgram(prog_id);
        GLint len = 0;
        glGetProgramInfoLog(prog_id, 65535, &len, output);
        if (len != 0)
            printf("Post-uniform len = %d:\n%s\n", len, output);
    }

    {
        uint32_t elementOp = codes->read_uint32(codes);
        assert(elementOp == OP_ELEMENT_ARRAY);
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
    }

    return 0;
}


// NOTE: Thread safety must be guaranteed externally
int vx_render(int width, int height)
{
    glClear (GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    glViewport(0,0,width,height);

    // debug: print stats
    printf("n resc %d, n vbos %d, n programs %d n tex %d\n",
           state.resource_map->size,state.vbo_map->size,
           state.program_map->size, state.texture_map->size);

    // For each buffer, process all the programs
    vhash_iterator_t itr;
    vhash_iterator_init(state.buffer_codes_map, &itr);
    char * buffer_name = NULL;
    while ((buffer_name = vhash_iterator_next_key(state.buffer_codes_map, &itr)) != NULL) {
        vx_code_input_stream_t *codes = vhash_get(state.buffer_codes_map, buffer_name);
        codes->reset(codes);

        printf("Rendering buffer: %s codes->len %d codes->pos %d\n", buffer_name, codes->len, codes->pos);
        //XXX need to reset codes to render multiple times

        while (!vx_render_program(codes));
    }

    return 0;
}


int vx_read_pixels_bgr(int width, int height, uint8_t * out_buf)
{

    glPixelStorei(GL_PACK_ALIGNMENT, 1);
    glReadPixels(0, 0, width, height, GL_BGR, GL_UNSIGNED_BYTE, out_buf);
    return 0;
}

int vx_deallocate(uint64_t * guids, int nguids)
{

    for (int i =0; i < nguids; i++) {
        uint64_t guid = guids[i];

        printf("Deallocating guid %ld:\n",guid);

        // There is always a resource for each guid.
        vx_resc_t * vr = lphash_remove(state.resource_map, guid).value;
        if (vr != NULL) {
            free(vr->res);
            free(vr);
        } else {
            printf("  Invalid request. Resource does not exist");
        }

        // There may also be a program, or a vbo or texture for each guid
        int vbo_success = 0;
        lihash_pair_t vbo_pair = lihash_remove(state.vbo_map, guid, &vbo_success);
        if (vbo_success) {
            // Tell open GL to deallocate this VBO
            glDeleteBuffers(1, &vbo_pair.value);
            printf(" Deleted VBO %d \n", vbo_pair.value);
        }

        int tex_success = 0;
        lihash_pair_t tex_pair = lihash_remove(state.texture_map, guid, &tex_success);
        if (tex_success) {
            // Tell open GL to deallocate this texture
            glDeleteTextures(1, &tex_pair.value);
            printf(" Deleted TEX %d \n", tex_pair.value);
        }

        gl_prog_resc_t * prog = lphash_remove(state.program_map, guid).value;
        if (prog != NULL) {

            glDetachShader(prog->prog_id,prog->vert_id);
            glDeleteShader(prog->vert_id);
            glDetachShader(prog->prog_id,prog->frag_id);
            glDeleteShader(prog->frag_id);
            glDeleteProgram(prog->prog_id);

            printf("  Freed program %d vert %d and frag %d\n",
                   prog->prog_id, prog->vert_id, prog->frag_id);
            free(prog);
        }

    }
    return 0;
}
