#include "vx.h"
#include <stdio.h>
#include <malloc.h>
#include <assert.h>

#include "vx_codes.h"
#include "glcontext.h"

#include "lphash.h"
#include "vhash.h"

#include "vx_resc.h"
#define MAX_FBOS 128




typedef struct vx vx_t;

struct vx
{
    glcontext_t *glc;
    gl_fbo_t *fbos[MAX_FBOS];

    lphash_t * resource_map;
    vhash_t * buffer_codes_map;


};


static vx_t state;

int vx_init()
{
    state.glc = glcontext_X11_create();

    state.resource_map = lphash_create();
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

            // XXX Also need to allocate VBOs at somepoint.
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

    assert(codes->read_uint32(codes) == OP_VERT_SHADER);
    uint64_t vertId = codes->read_uint64(codes);
    assert(codes->read_uint32(codes) == OP_FRAG_SHADER);
    uint64_t fragId = codes->read_uint64(codes);

    assert(codes->read_uint32(codes) == OP_ELEMENT_ARRAY);
    uint64_t elementId = codes->read_uint64(codes);
    uint32_t elementType = codes->read_uint32(codes);

    assert(codes->read_uint32(codes) == OP_VERT_ATTRIB_COUNT);
    uint32_t attribCount = codes->read_uint32(codes);

    for (int i = 0; i < attribCount; i++) {
        assert(codes->read_uint32(codes) == OP_VERT_ATTRIB);
        uint64_t attribId = codes->read_uint64(codes);
        uint32_t dim = codes->read_uint32(codes);
        char * name = codes->read_str(codes); //Not a copy!
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
