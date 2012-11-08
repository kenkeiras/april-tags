#ifndef _LIHASH_H
#define _LIHASH_H

#include <stdint.h>

/** lihash is for the special case where the key is a long. The value
 * is a 32-bit int. Note that the "get" function signature is
 * different to allow checking for get failures
 **/

struct lihash_element;

struct lihash_element
{
    uint64_t key;
    uint32_t value;

    struct lihash_element *next;
};

typedef struct
{
    int alloc;
    int size;

    struct lihash_element **elements;

} lihash_t;

typedef struct
{
    int bucket;
    struct lihash_element *el; // the next one to be returned.

    int has_next;

} lihash_iterator_t;

typedef struct
{
    uint64_t key;
    uint32_t value;
} lihash_pair_t;

lihash_t *lihash_create();
void lihash_destroy(lihash_t *vh);
uint32_t lihash_get(lihash_t *vh, uint64_t key, int * success);

// the old key will be retained in preference to the new key.
void lihash_put(lihash_t *vh, uint64_t key, uint32_t value);

void lihash_iterator_init(lihash_t *vh, lihash_iterator_t *vit);
uint64_t lihash_iterator_next_key(lihash_t *vh, lihash_iterator_t *vit);
int lihash_iterator_has_next(lihash_t *vh, lihash_iterator_t *vit);

// returns the removed element pair, which can be used for deallocation.
lihash_pair_t lihash_remove(lihash_t *vh, uint64_t key, int * success);

#endif
