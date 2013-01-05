#ifndef VX_LINES_H
#define VX_LINES_H

#include "vx_object.h"
#include "vx_resc.h"

vx_object_t * vx_lines_single_color4(vx_resc_t * points, float * color4, int npoints);
vx_object_t * vx_lines_multi_colored(vx_resc_t *points, vx_resc_t * colors, int npoints);

#endif
