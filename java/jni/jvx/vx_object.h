#ifndef __VX_OBJECT_
#define __VX_OBJECT_

#include <assert.h>

#include "lphash.h"
#include "vx_code_output_stream.h"
#include "vx_matrix_stack.h"

typedef struct vx_object vx_object_t;

struct vx_object
{
    void (*append)(vx_object_t * obj, lphash_t * resources, vx_code_output_stream_t * output, vx_matrix_stack_t * ms);
    void * impl;

    // Reference counting:
    uint32_t rcts; // how many places have a reference to this?
    void (*destroy)(vx_object_t * vo); // Destroy this object, and release all resources.
};

// Note: It is illegal to create a cycle of references with vx_objects. Not only will this
// break the reference counting, it would also result in infinitely long rendering codes

// Note: Subclasses must be sure to properly initialize the reference count to zero, e.g.:
//   vx_object_t * vo = calloc(1,sizeof(vx_object_t));


static inline void vx_object_dec_destroy(vx_object_t * obj)
{
    assert(obj->rcts > 0);
    obj->rcts--;
    if (obj->rcts == 0) {
        // Implementer must guarantee to release all resources, including
        // decrementing any reference counts it may have been holding
        obj->destroy(obj);
    }
}

static inline void vx_object_inc_ref(vx_object_t * obj)
{
    obj->rcts++;
}

#endif
