#include "vx_world.h"

#include <stdlib.h>

#include "vhash.h"

struct vx_world
{
    int worldID;
    vx_renderer_t * rend;
    vhash_t * buffer_map;
};

static int xxxAtomicID = 1;

vx_world_t * vx_world_create(vx_renderer_t * rend)
{
    vx_world_t *world = malloc(sizeof(vx_world_t));
    world->worldID = xxxAtomicID++;
    world->rend = rend;
    world->buffer_map = vhash_create(vhash_str_hash, vhash_str_equals);

    return world;
}
