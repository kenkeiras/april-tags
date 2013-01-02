#include <stdio.h>
#include "vhash.h"
#include <string.h>
#include <stdlib.h>
int main()
{
    printf("HelloWorld\n");

    vhash_t * vh = vhash_create(vhash_str_hash,vhash_str_equals);

    vhash_put(vh, strdup("foo"), strdup("bar"), NULL, NULL);
    vhash_put(vh, strdup("fooo"), strdup("barr"), NULL, NULL);
    vhash_put(vh, strdup("foooo"), strdup("barrr"), NULL, NULL);

    vhash_map2(vh, &free, &free);

    printf("free 0x%x, &free 0x%x\n", free, &free);

    vhash_destroy(vh);
}
