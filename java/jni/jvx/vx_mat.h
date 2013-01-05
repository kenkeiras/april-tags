#ifndef VX_MAT_H
#define VX_MAT_H

#include "vx_object.h"

vx_object_t * vx_mat_translate(double x, double y, double z);
vx_object_t * vx_mat_copy_from_doubles(double * mat44);


#endif
