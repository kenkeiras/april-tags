#ifndef __VX_LOCAL_RENDERER_H
#define __VX_LOCAL_RENDERER_H


// Methods which are defined only for local renderers
typedef struct vx_local_renderer vx_local_renderer_t;
typedef struct vx_local_state vx_local_state_t; //forward declaration
struct vx_local_renderer
{
    vx_renderer_t * super;
    vx_local_state_t * state; // data relating to local renderers (e.g. gl context, resource lists, etc)

    // XXX layer info
    void (*render)(vx_local_renderer_t * lrend, int width, int height, uint8_t *out_buf);
    void (*set_system_pm_matrix)(vx_local_renderer_t * lrend, float pm[]);
};

int vx_is_local_renderer(vx_renderer_t * rend);
vx_local_renderer_t * vx_get_local_renderer(vx_renderer_t * rend);
vx_local_renderer_t * vx_create_local_renderer(int initial_width, int initial_height);

#endif
