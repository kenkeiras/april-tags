#include "vx_points.h"
#include "GL/gl.h"
#include "vx_program.h"

struct {
    vx_resc_t * points;
    int dim;

    float size;
    float color[4];

    vx_resc_t * colors;

} vx_points_t;

vx_object_t * vx_points_single_color4(vx_resc_t * points, float * color4, int npoints)
{
    int pdim = points->count / npoints;

    vx_program_t * prog = vx_program_load_library("single-color");
    vx_program_set_vertex_attrib(prog, "position", points, pdim);
    vx_program_set_uniform4fv(prog, "color", color4);
    vx_program_set_draw_array(prog, npoints, GL_POINTS);
    return prog->super;
}

vx_object_t * vx_points_multi_colored(vx_resc_t *points, vx_resc_t * colors, int npoints)
{
    int pdim = points->count / npoints;
    int cdim = colors->count / npoints;

    vx_program_t * prog = vx_program_load_library("multi-colored");
    vx_program_set_vertex_attrib(prog, "position", points, pdim);
    vx_program_set_vertex_attrib(prog, "color", colors, cdim);
    vx_program_set_draw_array(prog, npoints, GL_POINTS);
    return prog->super;
}
