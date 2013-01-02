#ifndef __VX_RESOURCE_MANAGER_
#define __VX_RESOURCE_MANAGER_

#include "vx_renderer.h"
#include "varray.h"

typedef struct vx_resc_mgr vx_resc_mgr_t;

vx_resc_mgr_t* vx_resc_mgr_create(vx_renderer_t * rend);
void vx_resc_mgr_destroy(vx_resc_mgr_t* mgr);
void vx_resc_mgr_update_resources_managed(vx_resc_mgr_t * mgr, int worldID,
                                          char * buffer_name, lphash_t * resources);

#endif
