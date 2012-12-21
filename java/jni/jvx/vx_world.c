#include "vx_world.h"
#include <assert.h>
#include <stdlib.h>

#include "vhash.h"

struct vx_world
{
    int worldID;
    vx_renderer_t * rend;
    vhash_t * buffer_map;
};


struct vx_buffer
{
    char * name;
    vx_world_t * world;
    int draw_order;

    varray_t * objs; // stores vx_object_t's
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


vx_buffer_t * vx_world_get_buffer(vx_world_t * world, char * name)
{
    vx_buffer_t * buffer = vhash_get(world->buffer_map, name);
    if (buffer == NULL) {
        buffer = malloc(sizeof(vx_buffer_t));

        buffer->name = strdup(name);
        buffer->world = world;
        buffer->draw_order = 0;
        buffer->objs = varray_create();

        char * oldKey = NULL;
        void * oldValue= NULL;
        vhash_put(buffer->world->buffer_map, buffer->name, buffer, &oldKey, &oldValue);
        assert(oldValue == NULL);
    }

    return buffer;
}


void vx_buffer_stage(vx_buffer_t * buffer, vx_object_t * obj)
{
    varray_add(buffer->objs, obj);
}

void vx_buffer_commit(vx_buffer_t * buffer)
{
    varray_t * cobjs = buffer->objs;
    buffer->objs = varray_create();

    // send off the codes


    varray_destroy(cobjs);

}