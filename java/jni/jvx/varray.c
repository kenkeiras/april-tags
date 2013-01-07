#include "varray.h"
#include <math.h>
#include <stdlib.h>
#include <assert.h>
#include <stdarg.h>

#define MIN_ALLOC 16

struct varray
{
    int size;
    int alloc;
    void **data;
};

varray_t *varray_create()
{
    varray_t *va = calloc(1, sizeof(varray_t));
    return va;
}

varray_t *varray_create_varargs(void * first, ...)
{
    varray_t *va = varray_create();

    va_list vargs;
    va_start(vargs, first);
    for (void * vo = first; vo != NULL; vo = va_arg(vargs, void *))
        varray_add(va, vo);

    va_end(vargs);
    return va;
}

void varray_destroy(varray_t *va)
{
    if (va->data != NULL)
        free(va->data);
    free(va);
}

int varray_size(varray_t *va)
{
    return va->size;
}

void varray_add(varray_t *va, void *p)
{
    if (va->size == va->alloc) {
        int newalloc = va->alloc*2;
        if (newalloc < MIN_ALLOC)
            newalloc = MIN_ALLOC;
        va->data = realloc(va->data, sizeof(void*)*newalloc);
        va->alloc = newalloc;
    }

    va->data[va->size] = p;
    va->size++;
}

void varray_insert(varray_t *va, void *p, int idx, void *last_value)
{
    assert(idx < va-> size);

    void * prev = va->data[idx];
    // retain the previous value
    va->data[idx] = p;
    * ((void**)last_value) = prev;
}


void *varray_get(varray_t *va, int idx)
{
    assert(idx < va->size);
    return va->data[idx];
}

void *varray_remove(varray_t *va, int idx)
{
    assert(idx >= 0 && idx < va->size);

    void *p = va->data[idx];

    for (int i = idx+1; i < va->size; i++)
        va->data[i-1] = va->data[i];

    va->size--;

    return p;
}

void *varray_remove_shuffle(varray_t *va, int idx)
{
    assert(idx >= 0 && idx < va->size);

    void *p = va->data[idx];

    va->data[idx] = va->data[va->size-1];
    va->size--;
    return p;
}

void varray_remove_value(varray_t *va, void *d)
{
    int outpos = 0;
    for (int inpos = 0; inpos < va->size; inpos++) {
        if (va->data[inpos] == d)
            continue;
        va->data[outpos] = va->data[inpos];
        outpos++;
    }
    va->size = outpos;
}

void varray_map(varray_t *va, void (*f)())
{
    for (int i = 0; i < varray_size(va); i++)
        f(varray_get(va, i));
}
