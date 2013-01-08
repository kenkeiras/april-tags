#ifndef __VX_UTIL_
#define __VX_UTIL_
#include <stdint.h>

uint64_t vx_alloc_id();

uint64_t vx_mtime(); // returns the current time in milliseconds

void vx_util_unproject(double * point3, double * model_matrix, double * projection_matrix, int * viewport, double * vec3_out);

#endif
