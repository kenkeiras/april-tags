#include "vx_util.h"


uint64_t xxxAtomicLong = 1; //XXXX

uint64_t vx_alloc_id()
{
    return xxxAtomicLong++;
}
