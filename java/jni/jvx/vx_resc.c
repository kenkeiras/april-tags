#include "vx_resc.h"
#include <malloc.h>

void vx_resc_destroy(vx_resc_t * r)
{
    free(r->res);
    free(r);
}
