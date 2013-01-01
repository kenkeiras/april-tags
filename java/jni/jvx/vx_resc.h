#ifndef __VX_RESC_H__
#define __VX_RESC_H__

#include "stdint.h"


// Vx resources are one of the key elements of the toolkit -- each
// vx_resc_t associates a globalID with a block of memory.
// This allows us to only transmit the necessary stuff to a remote renderer
// and to manage the lifetime of memory allocated on the graphics card.
//
// In normal usage, the memory for a vx_resc_t  (and the associated chunk of memory)
// is automatically managed by vx using a reference counting system.
// When a new resource is created using one of the copy() functions,
// the user's memory is copied, and automatically managed (assuming
// the vx_resc_t is eventually passed to a vx_world_buffer as part of
// a vx_program);
//
// Advanced users may manage their own memory by specifying a custom destroy function
// and by incrementing the reference count appropriately


typedef struct vx_resc vx_resc_t;
struct vx_resc {
    uint32_t type; // GL_FLOAT, GL_BYTE, etc
    void* res;
    uint32_t count; // how many primitives?
    uint32_t fieldwidth; // how many bytes per primitive

    uint64_t id; // unique id for this resource

    // Reference counting:
    uint32_t rcts; // how many places have a reference to this?
    void * user;
    void (*destroy)(vx_resc_t * vr);

};

// call this function to decrement your reference count
// and destroy if yours was the last reference.
void vx_resc_dec_destroy(vx_resc_t * r);
void vx_resc_incr_ref(vx_resc_t * r);

// These methods all start with reference count = 0, so their memory will be managed
// by the vx_program to which they are passed.
vx_resc_t * vx_resc_load(char* path);
vx_resc_t * vx_resc_copyf(float * data, int count); // count, not total byte size
vx_resc_t * vx_resc_copyui(uint32_t * data, int count);
vx_resc_t * vx_resc_copyub(uint8_t * data, int count);


vx_resc_t * vx_resc_create_copy(void * data, int count, int fieldwidth, uint64_t id, int type);

#endif
