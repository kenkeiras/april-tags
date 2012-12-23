#ifndef __VX_RESOURCE_MANAGER_
#define __VX_RESOURCE_MANAGER_

#include "vx_renderer.h"
#include "varray.h"

typedef struct vx_resc_mgr vx_resc_mgr_t;

vx_resc_mgr_t* vx_resc_mgr_create(vx_renderer_t * rend);
vx_resc_mgr_t* vx_resc_mgr_destroy();
void vx_resc_mgr_update_resources_managed(vx_resc_mgr_t * mgr, int worldID,
                                          char * buffer_name, varray_t * resources);

#endif
