#ifndef VX_CHAIN_H
#define VX_CHAIN_H

#include "vx_object.h"

// Macros for creating a chain
# define vx_chain(...) _vx_chain_create_varargs_private(__VA_ARGS__, NULL)
# define vx_chain_add(vc, ...) _vx_chain_add_varargs_private(vc, __VA_ARGS__, NULL)


vx_object_t * vx_chain_create();
void vx_chain_add1(vx_object_t * chain, vx_object_t * first);

// NOTE: Both of the var args calls require the use of a NULL 'sentinenl' at the end of the argument list.
//       Most users should just use the macros (defined above), which automatically insert it.
//       e.g. vx_chain_create_varargs(vo1, vo2, vo3);
vx_object_t * _vx_chain_create_varargs_private(vx_object_t * first, ...);
void _vx_chain_add_varargs_private(vx_object_t * chain, vx_object_t * first, ...);

#endif
