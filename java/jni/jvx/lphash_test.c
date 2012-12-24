#include "lphash.h"

#include <stdio.h>
#include <assert.h>

int main(int argc, char ** argv)
{
    lphash_t *lp = lphash_create();

    lphash_put(lp, 45L, "char", NULL);
    lphash_put(lp, 4365L, "var", NULL);
    lphash_put(lp, 4165L, "mar", NULL);
    lphash_put(lp, 4655L, "sss", NULL);

    {
        lphash_iterator_t itr;
        lphash_iterator_init(lp, &itr);
        uint64_t key = 0;
        char * value = NULL;
        while ( lphash_iterator_next(&itr, &key, &value)){
            printf("%ld = %s\n",key, value);
            if (value[0] == 's')
                lphash_iterator_remove(&itr);
        }
    }

    lphash_remove(lp, 45L, NULL);
    assert(lphash_contains(lp, 4165L));

    {
        varray_t * vals = lphash_values(lp);
        for(int i = 0; i < varray_size(vals); i++){
            printf("%s\n",(char*)varray_get(vals, i));
        }
        varray_destroy(vals);
    }


    lphash_destroy(lp);
}
