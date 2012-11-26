#include "sort_util.h"
#include <stdio.h>


// candidate to move to varray.h?
// method avoids branching by doing 'extra' work if the idx's are the equal
static void varray_swap(varray_t * array, int idx1, int idx2)
{
    void * tmp = NULL;
    varray_insert(array, varray_get(array, idx2), idx1, &tmp);
    varray_insert(array, tmp, idx2, &tmp);
}

// Compute the pivot
static int varray_pivot(varray_t * array, int(*compare)(const void *a, const void * b),
                        int first, int last)
{
    int middle = (first + last)/2;
    // draw three samples to see which one is between the other two.
    //   when two (or more) of the samples are equal, default to 'middle' which is good default for sorted lists
    int pivot = middle;
    int c1 = compare(varray_get(array, first), varray_get(array, last));
    if (c1 < 0) {
        int c2 = compare(varray_get(array, last), varray_get(array, middle));
        if (c2 < 0) {
            pivot = last; // first < last < middle
        } else if (c1 > 0) {
            int c3 = compare(varray_get(array, first), varray_get(array, middle));
            if (c3 < 0)
                pivot = middle; // first < middle < last
            else if (c3 > 0)
                pivot = first; // middle < first < last
        }
    } else if (c1 > 0) {
        int c2 = compare(varray_get(array, last), varray_get(array, middle));
        if (c2 < 0) {
            int c3 = compare(varray_get(array, first), varray_get(array, middle));
            if (c3 < 0)
                pivot = first; // last < first < middle
            else if (c3 > 0)
                pivot = middle; //last < middle < first
        } else if (c2 > 0) {
            pivot = last; //  middle < last < first
        }
    }
    return pivot;
}

static void varray_quicksort_rec(varray_t * array, int(*compare)(const void *a, const void * b),
                                 int first, int last)
{
    // base case, list we need to sort is empty
    if (last <= first)
        return;


    // Step 1: find the pivot and move it to end of array
    int pivot = varray_pivot(array, compare, first, last);
    const void * pivot_v = varray_get(array, pivot);
    varray_swap(array, pivot, last);

    // Step 2: scan the array, from 'beg' to 'end' and move elements below the pivot to the beginning,
    int nextLower = first; // where will we keep the next value which is smaller than the pivot?
    for (int i = first; i <= last -1; i++) {
        if (compare(varray_get(array, i), pivot_v) < 0) { // item belongs in the lower half, no op
            varray_swap(array, nextLower, i);
            nextLower++;
        } // else, item belongs in the top half, so leave it in place
    }

    // Step 2.5 Finally, swap the pivot back
    varray_swap(array, last, nextLower);

    // Step 3: Recurse:
    varray_quicksort_rec(array, compare, first, nextLower-1); // don't resort the pivot
    varray_quicksort_rec(array, compare, nextLower+1, last); // don't resort the pivot
}

void varray_sort(varray_t * array, int(*compare)(const void *a, const void * b))
{
    varray_quicksort_rec(array, compare, 0, varray_size(array) -1);
}

