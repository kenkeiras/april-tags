#include "vx.h"
#include "glcontext.h"

#include "lphash.h"
#include "vx_resc.h"
#define MAX_FBOS 128




typedef struct vx vx_t;

struct vx
{
    glcontext_t *glc;
    gl_fbo_t *fbos[MAX_FBOS];

    lphash_t * resource_map;


};


static vx_t state;

int vx_init()
{
    state.glc = glcontext_X11_create();

    state.resource_map = lphash_create();

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

}

int vx_update_resources(int nresc, vx_resc_t ** resources)
{

}


int read_pixels_bgr(int width, int height, uint8_t * out_buf)
{
    glPixelStorei(GL_PACK_ALIGNMENT, 1);
    glReadPixels(0, 0, width, height, GL_BGR, GL_UNSIGNED_BYTE, out_buf);
}
