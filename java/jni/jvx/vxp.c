#include "vxp.h"
#include "vx_program.h"

vx_object_t * vxp_single_color(int npoints, vx_resc_t * points, float * color4, float pt_size, int type)
{
    int pdim = points->count / npoints;

    vx_program_t * prog = vx_program_load_library("single-color");
    vx_program_set_vertex_attrib(prog, "position", points, pdim);
    vx_program_set_uniform4fv(prog, "color", color4);
    vx_program_set_draw_array(prog, npoints, type);
    vx_program_set_line_width(prog, pt_size);
    return prog->super;
}

vx_object_t * vxp_multi_colored(int npoints, vx_resc_t * points, vx_resc_t * colors, float pt_size, int type)
{
    int pdim = points->count / npoints;
    int cdim = colors->count / npoints;

    vx_program_t * prog = vx_program_load_library("multi-colored");
    vx_program_set_vertex_attrib(prog, "position", points, pdim);
    vx_program_set_vertex_attrib(prog, "color", colors, cdim);
    vx_program_set_draw_array(prog, npoints, type);
    vx_program_set_line_width(prog, pt_size);
    return prog->super;
}


vx_object_t * vxp_single_color_indexed(vx_resc_t * indices, vx_resc_t * points, float * color4, float pt_size, int type)
{
    int npoints = indices->count;
    int pdim = points->count / npoints;

    vx_program_t * prog = vx_program_load_library("single-color");
    vx_program_set_vertex_attrib(prog, "position", points, pdim);
    vx_program_set_uniform4fv(prog, "color", color4);
    vx_program_set_element_array(prog, indices, type);
    vx_program_set_line_width(prog, pt_size);
    return prog->super;
}

vx_object_t * vxp_multi_colored_indexed(vx_resc_t * indices, vx_resc_t * points, vx_resc_t * colors, float pt_size, int type)
{
    int npoints = indices->count;
    int pdim = points->count / npoints;
    int cdim = colors->count / npoints;

    vx_program_t * prog = vx_program_load_library("multi-colored");
    vx_program_set_vertex_attrib(prog, "position", points, pdim);
    vx_program_set_vertex_attrib(prog, "color", colors, cdim);
    vx_program_set_element_array(prog, indices, type);
    vx_program_set_line_width(prog, pt_size);
    return prog->super;
}
