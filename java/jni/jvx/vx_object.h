#ifndef __VX_OBJECT_
#define __VX_OBJECT_

#include "lphash.h"
#include "vx_code_output_stream.h"
#include "vx_matrix_stack.h"


typedef struct vx_object vx_object_t;

struct vx_object
{
    void (*append)(vx_object_t * obj, lphash_t * resources, vx_code_output_stream_t * output, vx_matrix_stack_t * ms);
    void * impl;
};

#endif
