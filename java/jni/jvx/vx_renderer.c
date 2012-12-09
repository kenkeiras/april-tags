#include "vx_renderer.h"
#include "vx_local_renderer.h"

vx_renderer_t * vx_create(char * url)
{
    // XXX for now just always return local reference
    vx_local_renderer_t * lrend = vx_create_local_renderer(0,0);
    return lrend->super;
}
