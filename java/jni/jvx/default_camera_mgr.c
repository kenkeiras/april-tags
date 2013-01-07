#include "default_camera_mgr.h"

#include <assert.h>
#include <stdlib.h>

typedef struct default_mgr_state default_mgr_state_t;
struct default_mgr_state
{
    int foo;
};

static default_mgr_state_t * default_mgr_state_create()
{
    default_mgr_state_t * state = calloc(1, sizeof(default_mgr_state_t));
    return state;
}

static void default_destroy(vx_camera_mgr_t * cam)
{
    assert(0);
}

vx_camera_mgr_t * vx_camera_mgr_default_create()
{
    vx_camera_mgr_t * mgr =  calloc(1, sizeof(vx_camera_mgr_t));
    mgr->destroy = default_destroy;
    mgr->impl = default_mgr_state_create();
    return mgr;
}
