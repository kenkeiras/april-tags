#include <assert.h>
#include <malloc.h>
#include "larray.h"

void larray_ensure_size(larray_t * array, int newsize)
{
    if (newsize > array->alloc) {
        array->buf = realloc(array->buf, sizeof(uint64_t)*newsize);
        array->alloc = newsize;
    }
}

uint64_t larray_get(larray_t * array, uint32_t idx)
{
    assert(idx < array->size);
    return array->buf[idx];
}

void larray_add(larray_t * array, uint64_t value)
{
    larray_ensure_size(array, array->size+1);

    array->buf[array->size] = value;
    array->size++;
}

void larray_clear(larray_t * array)
{
    array->size = 0;
}

larray_t * larray_create()
{
    larray_t * array = calloc(sizeof(larray_t), 1); // all fields set to 0/NULL

    array->get = larray_get;
    array->add = larray_add;
    array->clear = larray_clear;

    larray_ensure_size(array, 10);

    return array;
}

void larray_destroy(larray_t * array)
{
    free(array->buf);
    free(array);
}
