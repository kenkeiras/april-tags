#ifndef __SORT_UTIL_H_
#define __SORT_UTIL_H_

#include "varray.h"

void varray_sort(varray_t * array, int(*comparator)(const void *a, const void * b));

#endif
