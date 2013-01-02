#ifndef __LARRAY_H_
#define __LARRAY_H_

#include <stdint.h>

typedef struct larray larray_t;
struct larray {
    uint64_t * buf;
    uint32_t size, alloc;

    uint64_t (* get)(larray_t * array, uint32_t idx);
    uint64_t (* remove)(larray_t * array, uint32_t idx);
    void (* add)(larray_t * array, uint64_t value);
    void (* clear)(larray_t * array);
    uint8_t (* contains)(larray_t * array, uint64_t value);
};


larray_t * larray_create();
void larray_destroy(larray_t * array);

#endif
