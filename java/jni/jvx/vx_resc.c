#include "vx_resc.h"
#include <malloc.h>

void vx_resc_destroy(vx_resc_t * r)
{
    if (r->res != NULL)
        free(r->res);
    free(r);
}
