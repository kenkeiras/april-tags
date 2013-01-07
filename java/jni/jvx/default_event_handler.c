#include "default_event_handler.h"
#include <assert.h>
#include <stdlib.h>

vx_event_handler_t * default_event_handler_create()
{
    vx_event_handler_t * eh = calloc(1, sizeof(vx_event_handler_t));
    assert(0);
    return eh;
}
