#ifndef __VX_RENDERER_H
#define __VX_RENDERER_H

#include "varray.h"
#include "vx_code_input_stream.h"
#include "vx_resc.h"

typedef struct vx_renderer vx_renderer_t;

struct vx_renderer
{
    int   impl_type;
    void *impl;

    // methods for the interface:
    void (*update_resources_managed)(vx_renderer_t * rend, int worldID, char * buffer_name, varray_t * resources);
    void (*add_resources_direct)(vx_renderer_t * rend, varray_t * resources);
    void (*remove_resources_direct)(vx_renderer_t * rend, varray_t * resources);
    void (*update_buffer)(vx_renderer_t * rend, int worldID, char * buffer_name, int drawOrder, vx_code_input_stream_t * codes);
    void (*update_layer)(vx_renderer_t * rend, int layerID, int worldID, int draw_order, float viewport_rel[4]);
    void (*get_canvas_size)(vx_renderer_t * rend, int * dim_out);
    void (*destroy)(vx_renderer_t * rend);
};

vx_renderer_t * vx_create_renderer(char * url);

#endif
