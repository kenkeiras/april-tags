#ifndef VX_MAT_H
#define VX_MAT_H

#include "vx_object.h"

vx_object_t * vx_mat_translate2(double x, double y);
vx_object_t * vx_mat_translate3(double x, double y, double z);
vx_object_t * vx_mat_scale1(double s);
vx_object_t * vx_mat_scale2(double sx, double sy);
vx_object_t * vx_mat_scale3(double sx, double sy, double sz);
vx_object_t * vx_mat_copy_from_doubles(double * mat44);


#endif
