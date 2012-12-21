#ifndef __VX_WORLD
#define __VX_WORLD

#include "vx_renderer.h"
#include "vx_object.h"

typedef struct vx_world vx_world_t;// forward reference
typedef struct vx_buffer vx_buffer_t;// forward reference

vx_world_t * vx_world_create(vx_renderer_t * rend);
vx_buffer_t * vx_world_get_buffer(vx_world_t * world, char * name);

// These calls are not thread safe -- need to be called from the same thread.
// E.g Don't access the same buffer from different threads
void vx_buffer_stage(vx_buffer_t * buffer, vx_object_t * obj);

// XXX How to deal with memory allocation! What to do with old vx_objects?
void vx_buffer_commit(vx_buffer_t * buffer);

#endif