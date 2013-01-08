#include "default_camera_mgr.h"

#include <assert.h>
#include <stdlib.h>
#include <string.h>
#include "vx_util.h"

typedef struct default_mgr_state default_mgr_state_t;
struct default_mgr_state
{
    /* double saved_eye[3]; */
    /* double saved_look_at[3]; */
    /* double saved_up[3]; */

    // our current view is somewhere between these two, based on time:
    double eye0[3];
    double lookat0[3];
    double up0[3];
    double perspectiveness0;
    uint64_t mtime0;

    double eye1[3];
    double lookat1[3];
    double up1[3];
    double perspectiveness1;
    uint64_t mtime1;

    double perspective_fovy_degrees;
    double zclip_near;
    double zclip_far;
};

static void scaled_combination(double * v1, double s1, double * v2, double s2, double * out, int len)
{
    for (int i = 0; i < len; i++)
        out[i] = v1[i]*s1 + v2[i]*s2;
}

static vx_camera_pos_t * default_get_camera_pos(vx_camera_mgr_t * mgr, int * viewport, uint64_t mtime)
{
    default_mgr_state_t * state = (default_mgr_state_t*)mgr->impl;

    vx_camera_pos_t * p = calloc(1, sizeof(vx_camera_pos_t));
    memcpy(p->viewport, viewport, 4*sizeof(int));

    p->perspective_fovy_degrees = state->perspective_fovy_degrees;
    p->zclip_near = state->zclip_near;
    p->zclip_far = state->zclip_far;

    if (mtime > state->mtime1) {
        memcpy(p->eye, state->eye1, 3*sizeof(double));
        memcpy(p->up, state->lookat1, 3*sizeof(double));
        memcpy(p->lookat, state->lookat1, 3*sizeof(double));
        p->perspectiveness = state->perspectiveness1;
    } else  if (mtime <= state->mtime0) {
        memcpy(p->eye, state->eye0, 3*sizeof(double));
        memcpy(p->up, state->lookat0, 3*sizeof(double));
        memcpy(p->lookat, state->lookat0, 3*sizeof(double));
        p->perspectiveness = state->perspectiveness0;
    } else {
        double alpha1 = ((double) mtime - state->mtime0) / (state->mtime1 - state->mtime0);
        double alpha0 = 1.0 - alpha1;

        scaled_combination(state->eye0,    alpha0, state->eye1,    alpha1, p->eye,    3);
        scaled_combination(state->up0,     alpha0, state->up1,     alpha1, p->up,     3);
        scaled_combination(state->lookat0, alpha0, state->lookat1, alpha1, p->lookat, 3);
        p->perspectiveness = state->perspectiveness0*alpha0 + state->perspectiveness1*alpha1;


        // XXX should add tweak from DefaultCameraManager.java
    }

    {
        memcpy(state->eye0, p->eye, 3*sizeof(double));
        memcpy(state->up0, p->lookat, 3*sizeof(double));
        memcpy(state->lookat0, p->lookat, 3*sizeof(double));
        state->perspectiveness0 = p->perspectiveness;
        state->mtime0 = mtime;
    }

    // XXX Need to do more fixup depending on interface mode!

    // XXX Need to prevent bad zooms

    return p;
}

static default_mgr_state_t * default_mgr_state_create()
{
    default_mgr_state_t * state = calloc(1, sizeof(default_mgr_state_t));
    state->eye1[2] = 100.0f;
    state->up1[1] = 1.0f;
    state->mtime1 = vx_mtime();

    /* state->interfaceMode = 2.5; */
    state->perspective_fovy_degrees = 50;
    state->zclip_near = 0.025;
    state->zclip_far = 50000;
    return state;
}

static void default_destroy(vx_camera_mgr_t * cam)
{
    assert(0);
}

vx_camera_mgr_t * vx_camera_mgr_default_create()
{
    vx_camera_mgr_t * mgr =  calloc(1, sizeof(vx_camera_mgr_t));
    mgr->get_camera_pos = default_get_camera_pos;
    mgr->destroy = default_destroy;
    mgr->impl = default_mgr_state_create();
    return mgr;
}
