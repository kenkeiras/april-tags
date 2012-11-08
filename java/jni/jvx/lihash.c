#include <stdlib.h>
#include <string.h>
#include <assert.h>

#include "lihash.h"

#define INITIAL_SIZE 16

// when the ratio of allocations to actual size drops below this
// ratio, we rehash. (Reciprocal of more typical load factor.)
#define REHASH_RATIO 2

lihash_t *lihash_create()
{
    lihash_t *vh = (lihash_t*) calloc(1, sizeof(lihash_t));

    vh->alloc = INITIAL_SIZE;
    vh->elements = (struct lihash_element**) calloc(vh->alloc, sizeof(struct lihash_element*));
    return vh;
}

// free all lihash_element structs. (does not free keys or values).
static void free_elements(struct lihash_element **elements, int alloc)
{
    for (int i = 0; i < alloc; i++) {
        struct lihash_element *el = elements[i];
        while (el != NULL) {
            struct lihash_element *nextel = el->next;
            free(el);
            el = nextel;
        }
    }
}

void lihash_destroy(lihash_t *vh)
{
    free_elements(vh->elements, vh->alloc);
    free(vh->elements);
    free(vh);
}

uint32_t lihash_get(lihash_t *vh, uint64_t key, int *success)
{
    uint64_t hash = key;
    int idx = hash % vh->alloc;

    *success = 1;
    struct lihash_element *el = vh->elements[idx];
    while (el != NULL) {
        if (el->key == key) {
            return el->value;
        }
        el = el->next;
    }

    *success = 0;
    return 0;
}

// returns one if a new element was added, 0 else. This is abstracted
// so that we can use it when put-ing and resizing.
static inline int lihash_put_real(lihash_t *vh, struct lihash_element **elements, int alloc, uint64_t key, uint32_t value)
{
    uint64_t hash = key;
    int idx = hash % alloc;

    // replace an existing key if it exists.
    struct lihash_element *el = elements[idx];
    while (el != NULL) {
        if (el->key == key) {
            el->value = value;
            return 0;
        }
        el = el->next;
    }

    // create a new key and prepend it to our linked list.
    el = (struct lihash_element*) calloc(1, sizeof(struct lihash_element));
    el->key = key;
    el->value = value;
    el->next = elements[idx];

    elements[idx] = el;
    return 1;
}

// returns number of elements removed
lihash_pair_t lihash_remove(lihash_t *vh, uint64_t key, int *success)
{
    uint64_t hash = key;
    int idx = hash % vh->alloc;

    struct lihash_element **out = &vh->elements[idx];
    struct lihash_element *in = vh->elements[idx];

    *success = 0;
    lihash_pair_t pair;
    pair.key = 0;
    pair.value = 0;

    while (in != NULL) {
        if (in->key == key) {
            // remove this element.
            pair.key = in->key;
            pair.value = in->value;

            struct lihash_element *tmp = in->next;
            free(in);
            in = tmp;

            *success = 1;
        } else {
            // keep this element (copy it back out)
            *out = in;
            out = &in->next;
            in = in->next;
        }
    }

    *out = NULL;
    return pair;
}

void lihash_put(lihash_t *vh, uint64_t key, uint32_t value)
{
    int added = lihash_put_real(vh, vh->elements, vh->alloc, key, value);
    vh->size += added;

    int ratio = vh->alloc / vh->size;

    if (ratio < REHASH_RATIO) {
        // resize
        int newalloc = vh->alloc*2;
        struct lihash_element **newelements = (struct lihash_element**) calloc(newalloc, sizeof(struct lihash_element*));

        // put all our existing elements into the new hash table
        for (int i = 0; i < vh->alloc; i++) {
            struct lihash_element *el = vh->elements[i];
            while (el != NULL) {
                lihash_put_real(vh, newelements, newalloc, el->key, el->value);
                el = el->next;
            }
        }

        // free the old elements
        free_elements(vh->elements, vh->alloc);

        // switch to the new elements
        vh->alloc = newalloc;
        vh->elements = newelements;
    }
}

static void lihash_iterator_find_next(lihash_t *vh, lihash_iterator_t *vit)
{
    // fetch the next one.

    // any more left in this bucket?
    if (vit->el != NULL)
        vit->el = vit->el->next;

    // search for the next non-empty bucket.
    while (vit->el == NULL) {
        if (vit->bucket + 1 == vh->alloc) {
            vit->el = NULL; // the end
            return;
        }

        vit->bucket++;
        vit->el = vh->elements[vit->bucket];
    }
}

void lihash_iterator_init(lihash_t *vh, lihash_iterator_t *vit)
{
    vit->bucket = -1;
    vit->el = NULL;
    lihash_iterator_find_next(vh, vit);
}

uint64_t lihash_iterator_next_key(lihash_t *vh, lihash_iterator_t *vit)
{
    if (vit->el == NULL) {
        // has_next would have returned false.
        assert(0);
        return 0;
    }

    uint64_t key = vit->el->key;

    lihash_iterator_find_next(vh, vit);

    return key;
}

int lihash_iterator_has_next(lihash_t *vh, lihash_iterator_t *vit)
{
    return (vit->el != NULL);
}

