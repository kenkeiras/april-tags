#ifndef __VX_OBJ_OPCODES__
#define __VX_OBJ_OPCODES__

#include "stdint.h"


// XXX Maybe this needs to be moved to vx_resc_mgr?
typedef struct vx_resc vx_resc_t;
struct vx_resc {
    uint32_t type;
    void* res;
    uint32_t length; // in units of sizeof(type)
    uint64_t id; // unique id for this resource

    // optional?
    char * name;
};

typedef struct vx_obj_opcodes vx_obj_opcodes_t;

struct vx_obj_opcodes
{
    // opcodes
    int ncodes;
    uint32_t * codes;

    // Tracking resources
    int nresc;
    vx_resc_t * rescs;
};


vx_obj_opcodes_t * vx_obj_opcodes_create(int ncodes, int nstrs, int nresc);

#endif
