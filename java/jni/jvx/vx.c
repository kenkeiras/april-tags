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

    lphash_t * resource_map;

    //XXX Really these should be lihash (64-bit key, 32 bit value, GLuint)
    //XXX Problem with lihash is how to distinguish a failed return
    lihash_t * program_map;
    lihash_t * vbo_map;

    vhash_t * buffer_codes_map;


};

typedef struct vbo_resc vbo_resc_t;
struct vbo_resc {

    vx_resc_t * vresc;

    GLuint vbo_id;
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

    state.program_map = lihash_create();
    state.vbo_map = lihash_create();

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

int vx_render_program(vx_code_input_stream_t * codes)
{
    if (codes->len == codes->pos) // exhausted the stream
        return 1;
    printf("Processing program, codes has %d remaining\n",codes->len-codes->pos);

    GLuint prog_id = -1;
    {
        uint32_t vertOp = codes->read_uint32(codes);
        assert(vertOp == OP_VERT_SHADER);
        uint64_t vertId = codes->read_uint64(codes);
        uint32_t fragOp = codes->read_uint32(codes);
        assert(fragOp == OP_FRAG_SHADER);
        uint64_t fragId = codes->read_uint64(codes);

        vx_resc_t * vertResc = lphash_get(state.resource_map, vertId);
        vx_resc_t * fragResc = lphash_get(state.resource_map, fragId);

        int success = 0;
        // Programs can be found by the guid of the either shader resource
        prog_id = lihash_get(state.program_map, vertId, &success);
        if (!success) {
            // Allocate a program if we haven't made it yet
            GLuint v = glCreateShader(GL_VERTEX_SHADER);
            GLuint f = glCreateShader(GL_FRAGMENT_SHADER);

            const char * vertSource = vertResc->res;
            const char * fragSource = fragResc->res;

            glShaderSource(v, 1, &vertSource, NULL);
            glShaderSource(f, 1, &fragSource, NULL);


            glCompileShader(v);
            glCompileShader(f);

            prog_id = glCreateProgram();

            glAttachShader(prog_id,v);
            glAttachShader(prog_id,f);

            glLinkProgram(prog_id);

            lihash_put(state.program_map, vertId, prog_id);
            lihash_put(state.program_map, fragId, prog_id);
        }
        glUseProgram(prog_id);

        printf("Vertex Shader:\n%s\n", (char *)vertResc->res);
        printf("Fragment Shader:\n%s\n", (char *)fragResc->res);
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
        if (!success) {
            printf("Allocating a VBO for guid %ld\n", attribId);
            glGenBuffers(1, &vbo_id);
            glBindBuffer(GL_ARRAY_BUFFER, vbo_id);
            glBufferData(GL_ARRAY_BUFFER, vr->count*vr->fieldwidth, vr->res, GL_STATIC_DRAW);
        }

        // Rebind, then attach
        glBindBuffer(GL_ARRAY_BUFFER, vbo_id);
        GLint attr_loc = glGetAttribLocation(prog_id, name);
        glVertexAttribPointer(attr_loc, dim, vr->type, 0, 0, 0); // XXX java link error
    }

    {
        uint32_t elementOp =codes->read_uint32(codes);
        assert(elementOp == OP_ELEMENT_ARRAY);
        uint64_t elementId = codes->read_uint64(codes);
        uint32_t elementType = codes->read_uint32(codes);

        // This should never fail!
        vx_resc_t * vr  = lphash_get(state.resource_map, elementId);
        assert(vr != NULL);


        int success = 0;
        GLuint vbo_id = lihash_get(state.vbo_map, elementId, &success);
        if (!success) {
            glGenBuffers(1, &vbo_id);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vbo_id);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, vr->count * vr->fieldwidth, vr->res, GL_STATIC_DRAW);
        } //XXX minor code duplication, see attributes

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vbo_id);
        glDrawElements(elementType, vr->count, vr->type, NULL);

    }

    return 0;
}

// NOTE: Thread safety must be guaranteed externally
int vx_render(int width, int height)
{
    glViewport(0,0,width,height);

    // For each buffer, process all the programs

    vhash_iterator_t itr;
    vhash_iterator_init(state.buffer_codes_map, &itr);
    char * buffer_name = NULL;
    while ((buffer_name = vhash_iterator_next_key(state.buffer_codes_map, &itr)) != NULL) {
        vx_code_input_stream_t *codes = vhash_get(state.buffer_codes_map, buffer_name);
        printf("Rendering buffer: %s codes->len %d codes->pos %d\n", buffer_name, codes->len, codes->pos);

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
