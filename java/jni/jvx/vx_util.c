#include "vx_util.h"
#include <stdlib.h>
#include <sys/time.h>


uint64_t xxxAtomicLong = 1; //XXXX

uint64_t vx_alloc_id()
{
    return xxxAtomicLong++;
}


uint64_t vx_mtime()
{
    // get the current time
    struct timeval tv;
    gettimeofday (&tv, NULL);
    return (int64_t) tv.tv_sec * 1000 + tv.tv_usec/1000;
}
