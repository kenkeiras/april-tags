#ifndef _VHASH_H
#define _VHASH_H

#include <stdint.h>

#include "varray.h"

#ifdef __cplusplus
extern "C" {
#endif

/** A basic hash table capable of storing a variety of key/value
 * pairs, with customizable hash() and equals() functions.
 *
 * The hash table does nothing about memory management for the keys
 * and values-- no internal copying, no freeing, etc. This means that
 * key/values that are "put" into the hashtable must remain
 * valid. Keys, in particular, should not be modified.
 *
 **/

typedef struct
{
    void *key;
    void *value;
} vhash_pair_t;

typedef struct vhash vhash_t;

// The contents of the iterator should be considered private. However,
// since our usage model prefers stack-based allocation of iterators,
// we must publicly declare them.
struct vhash_iterator
{
    vhash_t *vh;

    // these point to the next bucket/bucket-entry to be examined.
    int bucket;
    int idx;
};

typedef struct vhash_iterator vhash_iterator_t;

vhash_t *vhash_create(uint32_t(*hash)(const void *a), int(*equals)(const void *a, const void *b));
void vhash_destroy(vhash_t *vh);

// vhash_contains can tell the difference between a key being unmapped or mapped to NULL.
int vhash_contains(vhash_t *vh, const void *key);

// returns a new array containing all the keys in this hashtable
// user is responsible for eventually calling 'varray_destroy(key_set);'
varray_t * vhash_keys(vhash_t *vh);
varray_t * vhash_values(vhash_t *vh);

// returns NULL if the key is unmapped
void *vhash_get(vhash_t *vh, const void *key);

// The mapping key->value is established, replacing any previous
// mapping for key. The new key and new value will be stored in
// memory, potentially replacing and old key (even if it is "equal" to
// the new key) and value. The old key and old value are returned so
// that they can be deallocated if necessary. If there is no previous
// mapping for key, oldkey and oldvalue are set to NULL.
//
// oldkey/oldvalue may be NULL, in which case they are not returned.
//
// Returns 1 if a previous key/value mapping was found.
//
// example for a hashtable mapping char* to varray_t*.
//
// char *oldkey;
// varray_t *oldvalue;
//
// if (vhash_put(vh, newkey, newvalue, &oldkey, &oldvalue)) {  <-- note &'s
//    free(oldkey);
//    varray_map(free);
//    varray_destroy(oldvalue);
// }

int vhash_put(vhash_t *vh, void *key, void *value, void *oldkey, void *oldvalue);

// do not modify the hash table while iterating over the elements,
// though you can call vhash_iterator_remove.
void vhash_iterator_init(vhash_t *vh, vhash_iterator_t *vit);
int vhash_iterator_next(vhash_iterator_t *vit, void *key, void *value);

// remove the last returned key/value pair.
void vhash_iterator_remove(vhash_iterator_t *vit);

// returns 1 if the item was removed. If oldkey/oldvalue are non-NULL,
// they are set to the removed key/value, or NULL if no item was removed.
int vhash_remove(vhash_t *vh, void *key, void *oldkey, void *oldvalue);

int vhash_size(vhash_t *vh);

/////////////////////////////////////////////////////
// functions for keys that can be compared via their pointers.
uint32_t vhash_ptr_hash(const void *a);
int vhash_ptr_equals(const void *a, const void *b);

/////////////////////////////////////////////////////
// Functions for string-typed keys
uint32_t vhash_str_hash(const void *a);

int vhash_str_equals(const void *a, const void *b);

/////////////////////////////////////////////////////
// Functions for keys of type uint32_t. These methods cast the keys
// into the pointer, and thus assume that the size of a pointer is
// larger than 4 bytes.
// When calling put/get/remove, the key should be cast to (void*). e.g.,
// uint32_t key = 0x1234;
// vhash_put((void*) key, value);

uint32_t vhash_uint32_hash(const void *a);
int vhash_uint32_equals(const void *_a, const void *_b);

#ifdef __cplusplus
}
#endif

#endif
