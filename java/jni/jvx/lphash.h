#ifndef __LPHASH_H
#define __LPHASH_H

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

typedef struct lphash lphash_t;

// The contents of the iterator should be considered private. However,
// since our usage model prefers stack-based allocation of iterators,
// we must publicly declare them.
struct lphash_iterator
{
    lphash_t *vh;

    // these point to the next bucket/bucket-entry to be examined.
    int bucket;
    int idx;
};

typedef struct lphash_iterator lphash_iterator_t;

lphash_t *lphash_create();
void lphash_destroy(lphash_t *vh);

// lphash_contains can tell the difference between a key being unmapped or mapped to NULL.
int lphash_contains(lphash_t *vh, const uint64_t key);

// returns a new array containing all the keys in this hashtable
// user is responsible for eventually calling 'varray_destroy(key_set);'
varray_t * lphash_values(lphash_t *vh);

// returns NULL if the key is unmapped
void *lphash_get(lphash_t *vh, const uint64_t key);

// The mapping key->value is established, replacing any previous
// mapping for key. The new key and new value will be stored in
// memory, potentially replacing and old key (even if it is "equal" to
// the new key) and value. The old value is returned so
// that they can be deallocated if necessary. If there is no previous
// mapping for key, oldkey and oldvalue are set to NULL.
//
// oldvalue may be NULL, in which case it is not returned.
//
// Returns 1 if a previous key/value mapping was found.
//
// example for a hashtable of varray_t*.
//
// varray_t *oldvalue;
//
// if (lphash_put(vh, newkey, newvalue, &oldvalue)) {  <-- note &'s
//    varray_map(free);
//    varray_destroy(oldvalue);
// }

int lphash_put(lphash_t *vh, uint64_t key, void *value, void *oldvalue);

// do not modify the hash table while iterating over the elements,
// though you can call lphash_iterator_remove.
void lphash_iterator_init(lphash_t *vh, lphash_iterator_t *vit);


// usage:
// uint64_t key;
// varray_t * value;
// while(lphash_iterator_next(&itr, &key, &value)) ...
int lphash_iterator_next(lphash_iterator_t *vit, uint64_t *key, void *value);

// remove the last returned key/value pair.
void lphash_iterator_remove(lphash_iterator_t *vit);

// returns 1 if the item was removed. If oldkey/oldvalue are non-NULL,
// they are set to the removed key/value, or NULL if no item was removed.
int lphash_remove(lphash_t *vh, uint64_t key, void *oldvalue);

int lphash_size(lphash_t *vh);

lphash_t * lphash_copy(lphash_t *vh);


#ifdef __cplusplus
}
#endif

#endif
