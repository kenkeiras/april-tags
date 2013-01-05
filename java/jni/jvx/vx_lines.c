#include "vx_lines.h"
#include "GL/gl.h"
#include "vx_program.h"

vx_object_t * vx_lines_single_color4(vx_resc_t * lines, float * color4, int nlines)
{
    int pdim = lines->count / nlines;

    vx_program_t * prog = vx_program_load_library("single-color");
    vx_program_set_vertex_attrib(prog, "position", lines, pdim);
    vx_program_set_uniform4fv(prog, "color", color4);
    vx_program_set_draw_array(prog, nlines, GL_LINES);
    return prog->super;
}

vx_object_t * vx_lines_multi_colored(vx_resc_t *lines, vx_resc_t * colors, int nlines)
{
    int pdim = lines->count / nlines;
    int cdim = colors->count / nlines;

    vx_program_t * prog = vx_program_load_library("multi-colored");
    vx_program_set_vertex_attrib(prog, "position", lines, pdim);
    vx_program_set_vertex_attrib(prog, "color", colors, cdim);
    vx_program_set_draw_array(prog, nlines, GL_LINES);
    return prog->super;
}
