#ifndef _VARRAY_H
#define _VARRAY_H

#ifdef __cplusplus
extern "C" {
#endif

typedef struct varray varray_t;

varray_t *varray_create();
void varray_destroy(varray_t *va);
int varray_size(varray_t *va);
void varray_add(varray_t *va, void *p);

// Correct usage:
// void * prev_value = NULL;
// varray_instert(array, next_value, 5, &prev_value);
// Thus implementation allows reuse of the same pointer for insertion and to record the old value:
// varray_instert(array, value, 5, &value);
void varray_insert(varray_t *va, void *p, int idx, void *last_value);
void *varray_get(varray_t *va, int idx);

// remove the idx'th element, returning the element that was removed
// and moving all subsequent entries down one index position.
void *varray_remove(varray_t *va, int idx);

// remove the idx'th element, returning the element that was removed
// and moving the last entry of the array to the newly-empty position.
void *varray_remove_shuffle(varray_t *va, int idx);

// search the array for the specified value and remove it.
void varray_remove_value(varray_t *va, void *d);

// apply function f() to each element of the varray. Useful for
// freeing the elements. Do not modify the contents of the varray from
// within the callback function.
void varray_map(varray_t *va, void (*f)());

#ifdef __cplusplus
}
#endif

#endif
