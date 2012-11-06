#include "vx.h"
#include "glcontext.h"

#define MAX_FBOS 128

static glcontext_t *glc;
static gl_fbo_t *fbos[MAX_FBOS];


int init_glcontext()
{
    glc = glcontext_X11_create();
    return 0;
}

int fbo_create(int width, int height)
{
    gl_fbo_t *fbo = gl_fbo_create(glc, width, height);

    if (fbo == NULL)
        return -1;

    // skip id 0...
    for (int i = 1; i < MAX_FBOS; i++) {
        if (fbos[i] == NULL) {
            fbos[i] = fbo;
            return i;
        }
    }

    return -2;

}
