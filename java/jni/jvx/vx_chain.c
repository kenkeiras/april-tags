#include "vx_chain.h"

#include <stdarg.h>
#include <stdlib.h>
#include <stdio.h>

typedef struct vx_chain vx_chain_t;
struct vx_chain
{
    vx_object_t * super;

    varray_t * objs;
};

static void vx_chain_append (vx_object_t * obj, lphash_t * resources, vx_code_output_stream_t * codes, vx_matrix_stack_t * ms)
{
    vx_matrix_stack_push(ms);
    vx_chain_t *vc = (vx_chain_t *) obj->impl;
    for (int i = 0; i < varray_size(vc->objs); i++) {
        vx_object_t * vo = varray_get(vc->objs, i);
        vo->append(vo, resources, codes, ms);
    }
    vx_matrix_stack_pop(ms);
}

static void vx_chain_destroy(vx_object_t * vo)
{
    vx_chain_t * vc = (vx_chain_t*)vo->impl;
    varray_map(vc->objs, vx_object_dec_destroy);
    varray_destroy(vc->objs);
    free(vo);
    free(vc);
}

static void vx_chain_add_real(vx_chain_t * vc, vx_object_t * first, va_list va)
{
    for (vx_object_t * vo = first; vo != NULL; vo = va_arg(va, vx_object_t *))
        vx_chain_add1(vc->super, vo);
}

vx_object_t * vx_chain_create()
{
    vx_chain_t * vc = calloc(1, sizeof(vx_chain_t));
    vc->super = calloc(1, sizeof(vx_object_t));
    vc->super->impl = vc;
    vc->super->destroy = vx_chain_destroy;
    vc->super->append = vx_chain_append;
    vc->objs = varray_create();
    return vc->super;
}

vx_object_t * _vx_chain_create_varargs_private(vx_object_t * first, ...) {
    vx_object_t * vo  = vx_chain_create();
    va_list va;
    va_start(va, first);
    vx_chain_add_real((vx_chain_t*)vo->impl, first, va);
    va_end(va);
    return vo;
}


void _vx_chain_add_varargs_private(vx_object_t * vo, vx_object_t * first, ...)
{
    va_list va;
    va_start(va, first);
    vx_chain_add_real((vx_chain_t*)vo->impl, first, va);
    va_end(va);
}

void vx_chain_add1(vx_object_t * vo, vx_object_t * first)
{
    vx_object_inc_ref(vo);
    varray_add(((vx_chain_t*)vo->impl)->objs, first);
}
