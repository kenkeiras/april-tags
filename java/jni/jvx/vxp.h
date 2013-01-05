#ifndef VXP_H
#define VXP_H

#include "vx_object.h"
#include "vx_resc.h"

vx_object_t * vxp_single_color(int npoints, vx_resc_t * points, float * color4, float pt_size, int type);
vx_object_t * vxp_multi_colored(int npoints, vx_resc_t * points, vx_resc_t * colors, float pt_size, int type);

vx_object_t * vxp_single_color_indexed(int npoints, vx_resc_t * points, float * color4, float pt_size, int type, vx_resc_t * indices);
vx_object_t * vxp_multi_colored_indexed(int npoints, vx_resc_t * points, vx_resc_t * colors, float pt_size, int type, vx_resc_t * indices);

#endif
