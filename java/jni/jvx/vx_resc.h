#ifndef __VX_RESC_H__
#define __VX_RESC_H__

#include "stdint.h"


// XXX Maybe this needs to be moved to vx_resc_mgr?
typedef struct vx_resc vx_resc_t;
struct vx_resc {
    uint32_t type; // GL_FLOAT, GL_BYTE, etc
    void* res;
    uint32_t count; // in units of sizeof(type)
    uint32_t fieldwidth; // how many bytes per primitive

    uint64_t id; // unique id for this resource

};

void vx_resc_destroy(vx_resc_t * r);

vx_resc_t * vx_resc_load(char* path);

#endif
