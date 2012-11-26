#include <stdio.h>
#include <stdlib.h>
#include "varray.h"
#include "sort_util.h"

typedef struct ranked_str ranked_str_t;
struct ranked_str
{
    char * str;
    int rank;
};

int ranked_str_compare(const void *va, const void * vb)
{
    const ranked_str_t * a = (const ranked_str_t *) va;
    const ranked_str_t * b = (const ranked_str_t *) vb;
    return a->rank - b->rank;
}

int main()
{

    varray_t * array = varray_create();

    ranked_str_t first = {
        "first",
        -10};

    ranked_str_t second = {
        "second",
        -9};

    ranked_str_t third = {
        "third",
        9};

    ranked_str_t sm = {
        "same",
        10};

    ranked_str_t sm2 = {
        "same2",
        10};
    ranked_str_t sl = {
        "second-to-last",
        11};
    ranked_str_t last = {
        "last",
        100};

    varray_add(array, &third);
    varray_add(array, &first);
    varray_add(array, &sm2);
    varray_add(array, &last);
    varray_add(array, &sm);
    varray_add(array, &sl);
    varray_add(array, &second);


    printf("Initial ordering:\n");
    for (int i = 0; i < varray_size(array); i++) {
        ranked_str_t * ptr = varray_get(array, i);
        printf(" [%d] =   {% d : %s}\n",i, ptr->rank, ptr->str);
    }


    varray_sort(array, ranked_str_compare);

    printf("Initial ordering:\n");
    for (int i = 0; i < varray_size(array); i++) {
        ranked_str_t * ptr = varray_get(array, i);
        printf(" [%d] =   {% d : %s}\n",i, ptr->rank, ptr->str);
    }

}
