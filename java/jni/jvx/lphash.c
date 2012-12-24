#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "lphash.h"

#define INITIAL_NBUCKETS 16
#define INITIAL_BUCKET_CAPACITY 4

// when the ratio of allocations to actual size drops below this
// ratio, we rehash. (Reciprocal of more typical load factor.)
#define REHASH_RATIO 2

struct bucket_entry
{
    uint64_t key;
    void *value;
};

struct bucket
{
    uint32_t size; // # entries in this bucket
    uint32_t alloc; // # entries allocated
    struct bucket_entry *entries;
};

struct lphash
{
    int size; // # of items in hash table

    struct bucket *buckets;
    int nbuckets;
};

lphash_t *lphash_create()
{
    lphash_t *vh = (lphash_t*) calloc(1, sizeof(lphash_t));

    vh->nbuckets = INITIAL_NBUCKETS;
    vh->buckets = (struct bucket*) calloc(vh->nbuckets, sizeof(struct bucket));
    return vh;
}

lphash_t * lphash_copy(lphash_t * orig)
{
    lphash_t * out = lphash_create();
    {
        lphash_iterator_t itr;
        lphash_iterator_init(orig, &itr);
        uint64_t key = 0;
        void * value = NULL;
        while ( lphash_iterator_next(&itr, &key, &value))
            lphash_put(out, key, value, NULL);
    }
    return out;
}

void lphash_destroy(lphash_t *vh)
{
    for (int i = 0; i < vh->nbuckets; i++)
        free(vh->buckets[i].entries);
    free(vh->buckets);
    free(vh);
}

int lphash_size(lphash_t *vh)
{
    return vh->size;
}

int lphash_contains(lphash_t *vh, uint64_t key)
{
    int idx = key % vh->nbuckets;
    struct bucket *bucket = &vh->buckets[idx];
    for (int i = 0; i < bucket->size; i++) {
        if (key == bucket->entries[i].key)
            return 1;
    }

    return 0;
}

void *lphash_get(lphash_t *vh, uint64_t key)
{
    int idx = key % vh->nbuckets;

    struct bucket *bucket = &vh->buckets[idx];
    for (int i = 0; i < bucket->size; i++) {
        if (key ==  bucket->entries[i].key)
            return bucket->entries[i].value;
    }

    return NULL;
}

// returns 1 if there was an oldkey/oldvalue.
static inline int lphash_put_real(lphash_t *vh, struct bucket *buckets, int nbuckets, uint64_t key, void *value, void *oldvalue)
{
    int idx = key % nbuckets;
    struct bucket *bucket = &buckets[idx];

    // replace an existing key if it exists.
    for (int i = 0; i < bucket->size; i++) {
        if (key ==  bucket->entries[i].key) {
            if (oldvalue)
                *((void**) oldvalue) = bucket->entries[i].value;

            bucket->entries[i].key = key;
            bucket->entries[i].value = value;
            return 1;
        }
    }

    // enlarge bucket?
    if (bucket->size == bucket->alloc) {
        if (bucket->alloc == 0)
            bucket->alloc = INITIAL_BUCKET_CAPACITY;
        else
            bucket->alloc *= 2;

        bucket->entries = realloc(bucket->entries, bucket->alloc * sizeof(struct bucket_entry));
    }

    // add!
    bucket->entries[bucket->size].key = key;
    bucket->entries[bucket->size].value = value;
    bucket->size++;

    if (oldvalue)
        *((void**) oldvalue) = NULL;

    return 0;
}

int lphash_remove(lphash_t *vh, uint64_t key, void *oldvalue)
{
    int idx = key % vh->nbuckets;
    struct bucket *bucket = &vh->buckets[idx];

    // replace an existing key if it exists.
    for (int i = 0; i < bucket->size; i++) {
        if (key == bucket->entries[i].key) {
            if (oldvalue)
                *((void**) oldvalue) = bucket->entries[i].value;

            // shuffle remove.
            bucket->entries[i] = bucket->entries[bucket->size-1];
            bucket->size--;
            vh->size--;

            return 1;
        }
    }

    if (oldvalue)
        *((void**) oldvalue) = NULL;

    return 0;
}

int lphash_put(lphash_t *vh, uint64_t key, void *value, void *oldvalue)
{
    if (vh->nbuckets * REHASH_RATIO < vh->size) {

        // resize
        int new_nbuckets = vh->nbuckets*2;
        struct bucket *new_buckets = calloc(new_nbuckets, sizeof(struct bucket));

        // put all our existing elements into the new hash table
        lphash_iterator_t vit;
        lphash_iterator_init(vh, &vit);
        uint64_t key, *value;
        while (lphash_iterator_next(&vit, &key, &value)) {
            lphash_put_real(vh, new_buckets, new_nbuckets, key, value, NULL);
        }

        // free the old elements
        for (int i = 0; i < vh->nbuckets; i++)
            free(vh->buckets[i].entries);
        free(vh->buckets);

        // switch to the new elements
        vh->nbuckets = new_nbuckets;
        vh->buckets = new_buckets;
    }

    int has_oldkey = lphash_put_real(vh, vh->buckets, vh->nbuckets, key, value, oldvalue);
    if (!has_oldkey)
        vh->size++;

    return has_oldkey;
}

void lphash_iterator_init(lphash_t *vh, lphash_iterator_t *vit)
{
    vit->vh = vh;
    vit->bucket = 0;
    vit->idx = 0;
}

// Supply a pointer to a pointer
// e.g. char* key; varray_t *value; int res = lphash_iterator_next(&vit, &key, &value);
int lphash_iterator_next(lphash_iterator_t *vit, uint64_t * key, void *value)
{
    lphash_t *vh = vit->vh;

    while (vit->bucket < vh->nbuckets) {

        if (vit->idx < vh->buckets[vit->bucket].size) {
            *key = vh->buckets[vit->bucket].entries[vit->idx].key;
            *((void**) value) = vh->buckets[vit->bucket].entries[vit->idx].value;
            vit->idx++;

            return 1;
        }

        vit->bucket++;
        vit->idx = 0;
    }

    return 0;
}

void lphash_iterator_remove(lphash_iterator_t *vit)
{
    lphash_t *vh = vit->vh;

    uint64_t key = vh->buckets[vit->bucket].entries[vit->idx-1].key;

    lphash_remove(vh, key, NULL);
    vit->idx--;
}

varray_t * lphash_values(lphash_t * vh)
{
    varray_t * values = varray_create();

    for (int i = 0; i < vh->nbuckets; i++) {
        for (int j = 0; j < vh->buckets[i].size; j++)
            varray_add(values, vh->buckets[i].entries[j].value);
    }
    return values;
}
