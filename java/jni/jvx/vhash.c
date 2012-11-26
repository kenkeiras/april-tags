#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "vhash.h"

#define INITIAL_NBUCKETS 16
#define INITIAL_BUCKET_CAPACITY 4

// when the ratio of allocations to actual size drops below this
// ratio, we rehash. (Reciprocal of more typical load factor.)
#define REHASH_RATIO 2

struct bucket_entry
{
    void *key;
    void *value;
};

struct bucket
{
    uint32_t size; // # entries in this bucket
    uint32_t alloc; // # entries allocated
    struct bucket_entry *entries;
};

struct vhash
{
    uint32_t(*hash)(const void *a);

    // returns 1 if equal
    int(*equals)(const void *a, const void *b);

    int size; // # of items in hash table

    struct bucket *buckets;
    int nbuckets;
};

vhash_t *vhash_create(uint32_t(*hash)(const void *a), int(*equals)(const void *a, const void*b))
{
    vhash_t *vh = (vhash_t*) calloc(1, sizeof(vhash_t));
    vh->hash = hash;
    vh->equals = equals;

    vh->nbuckets = INITIAL_NBUCKETS;
    vh->buckets = (struct bucket*) calloc(vh->nbuckets, sizeof(struct bucket));
    return vh;
}

void vhash_destroy(vhash_t *vh)
{
    for (int i = 0; i < vh->nbuckets; i++)
        free(vh->buckets[i].entries);
    free(vh->buckets);
    free(vh);
}

int vhash_size(vhash_t *vh)
{
    return vh->size;
}

int vhash_contains(vhash_t *vh, const void *key)
{
    uint32_t hash = vh->hash(key);
    int idx = hash % vh->nbuckets;

    struct bucket *bucket = &vh->buckets[idx];
    for (int i = 0; i < bucket->size; i++) {
        if (vh->equals(key, bucket->entries[i].key))
            return 1;
    }

    return 0;
}

void *vhash_get(vhash_t *vh, const void *key)
{
    uint32_t hash = vh->hash(key);
    int idx = hash % vh->nbuckets;

    struct bucket *bucket = &vh->buckets[idx];
    for (int i = 0; i < bucket->size; i++) {
        if (vh->equals(key, bucket->entries[i].key))
            return bucket->entries[i].value;
    }

    return NULL;
}

// returns 1 if there was an oldkey/oldvalue.
static inline int vhash_put_real(vhash_t *vh, struct bucket *buckets, int nbuckets, void *key, void *value, void *oldkey, void *oldvalue)
{
    uint32_t hash = vh->hash(key);

    int idx = hash % nbuckets;
    struct bucket *bucket = &buckets[idx];

    // replace an existing key if it exists.
    for (int i = 0; i < bucket->size; i++) {
        if (vh->equals(key, bucket->entries[i].key)) {
            if (oldkey)
                *((void**) oldkey) = bucket->entries[i].key;
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

    if (oldkey)
        *((void**) oldkey) = NULL;
    if (oldvalue)
        *((void**) oldvalue) = NULL;

    return 0;
}

int vhash_remove(vhash_t *vh, void *key, void *oldkey, void *oldvalue)
{
    uint32_t hash = vh->hash(key);

    int idx = hash % vh->nbuckets;
    struct bucket *bucket = &vh->buckets[idx];

    // replace an existing key if it exists.
    for (int i = 0; i < bucket->size; i++) {
        if (vh->equals(key, bucket->entries[i].key)) {
            if (oldkey)
                *((void**) oldkey) = bucket->entries[i].key;
            if (oldvalue)
                *((void**) oldvalue) = bucket->entries[i].value;

            // shuffle remove.
            bucket->entries[i] = bucket->entries[bucket->size-1];
            bucket->size--;
            vh->size--;

            return 1;
        }
    }

    if (oldkey)
        *((void**) oldkey) = NULL;
    if (oldvalue)
        *((void**) oldvalue) = NULL;

    return 0;
}

int vhash_put(vhash_t *vh, void *key, void *value, void *oldkey, void *oldvalue)
{
    if (vh->nbuckets * REHASH_RATIO < vh->size) {

        // resize
        int new_nbuckets = vh->nbuckets*2;
        struct bucket *new_buckets = calloc(new_nbuckets, sizeof(struct bucket));

        // put all our existing elements into the new hash table
        vhash_iterator_t vit;
        vhash_iterator_init(vh, &vit);
        void *key, *value;
        while (vhash_iterator_next(&vit, &key, &value)) {
            vhash_put_real(vh, new_buckets, new_nbuckets, key, value, NULL, NULL);
        }

        // free the old elements
        for (int i = 0; i < vh->nbuckets; i++)
            free(vh->buckets[i].entries);
        free(vh->buckets);

        // switch to the new elements
        vh->nbuckets = new_nbuckets;
        vh->buckets = new_buckets;
    }

    int has_oldkey = vhash_put_real(vh, vh->buckets, vh->nbuckets, key, value, oldkey, oldvalue);
    if (!has_oldkey)
        vh->size++;

    return has_oldkey;
}

void vhash_iterator_init(vhash_t *vh, vhash_iterator_t *vit)
{
    vit->vh = vh;
    vit->bucket = 0;
    vit->idx = 0;
}

// Supply a pointer to a pointer
// e.g. char* key; varray_t *value; int res = vhash_iterator_next(&vit, &key, &value);
int vhash_iterator_next(vhash_iterator_t *vit, void *key, void *value)
{
    vhash_t *vh = vit->vh;

    while (vit->bucket < vh->nbuckets) {

        if (vit->idx < vh->buckets[vit->bucket].size) {
            *((void**) key) = vh->buckets[vit->bucket].entries[vit->idx].key;
            *((void**) value) = vh->buckets[vit->bucket].entries[vit->idx].value;
            vit->idx++;

            return 1;
        }

        vit->bucket++;
        vit->idx = 0;
    }

    return 0;
}

void vhash_iterator_remove(vhash_iterator_t *vit)
{
    vhash_t *vh = vit->vh;

    void *key = vh->buckets[vit->bucket].entries[vit->idx-1].key;

    vhash_remove(vh, key, NULL, NULL);
    vit->idx--;
}

varray_t * vhash_keys(vhash_t * vh)
{
    varray_t * key_set = varray_create();

    for (int i = 0; i < vh->nbuckets; i++) {
        for (int j = 0; j < vh->buckets[i].size; j++)
            varray_add(key_set, vh->buckets[i].entries[j].key);
    }
    return key_set;
}

varray_t * vhash_values(vhash_t * vh)
{
    varray_t * values = varray_create();

    for (int i = 0; i < vh->nbuckets; i++) {
        for (int j = 0; j < vh->buckets[i].size; j++)
            varray_add(values, vh->buckets[i].entries[j].value);
    }
    return values;
}

uint32_t vhash_str_hash(const void *_a)
{
    char *a = (char*) _a;

    int64_t hash = 0;
    while (*a != 0) {
        hash += *a;
        hash = (hash << 7) + (hash >> 23);
        a++;
    }

    return hash;
}

union uintpointer
{
    const void *p;
    uint32_t i;
};

int vhash_str_equals(const void *_a, const void *_b)
{
    char *a = (char*) _a;
    char *b = (char*) _b;

    return !strcmp(a, b);
}

uint32_t vhash_ptr_hash(const void *_a)
{
    union uintpointer ip;

    ip.i = 0; // make sure any extra bits in i are cleared.
    ip.p = _a;

    uint32_t hash = ip.i;
    hash ^= (hash >> 7);

    return hash;
}

int vhash_ptr_equals(const void *_a, const void *_b)
{
    return _a == _b;
}

uint32_t vhash_uint32_hash(const void *_a)
{
    union uintpointer ip;

    ip.i = 0; // make sure any extra bits in i are cleared.
    ip.p = _a;

    return ip.i;
}

int vhash_uint32_equals(const void *_a, const void *_b)
{
    union uintpointer ipa, ipb;

    ipa.i = 0;
    ipa.p = _a;
    ipb.i = 0;
    ipb.p = _b;

    return (ipa.i == ipb.i);
}
