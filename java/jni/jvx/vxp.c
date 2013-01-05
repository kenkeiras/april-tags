#include "vxp.h"
#include "vx_program.h"
#include <stdio.h>
#include <GL/gl.h>
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


vx_object_t * vxp_single_color_indexed(int npoints, vx_resc_t * points, float * color4, float pt_size, int type, vx_resc_t * indices)
{
    int pdim = points->count / npoints;

    vx_program_t * prog = vx_program_load_library("single-color");
    vx_program_set_vertex_attrib(prog, "position", points, pdim);
    vx_program_set_uniform4fv(prog, "color", color4);
    vx_program_set_element_array(prog, indices, type);
    vx_program_set_line_width(prog, pt_size);
    return prog->super;
}

vx_object_t * vxp_multi_colored_indexed(int npoints, vx_resc_t * points, vx_resc_t * colors, float pt_size, int type, vx_resc_t * indices)
{
    int pdim = points->count / npoints;
    int cdim = colors->count / npoints;

    vx_program_t * prog = vx_program_load_library("multi-colored");
    vx_program_set_vertex_attrib(prog, "position", points, pdim);
    vx_program_set_vertex_attrib(prog, "color", colors, cdim);
    vx_program_set_element_array(prog, indices, type);
    vx_program_set_line_width(prog, pt_size);
    return prog->super;
}


vx_object_t * vxp_texture(int npoints, vx_resc_t * points, vx_resc_t * texcoords,
                          vx_resc_t * tex, int width, int height, int format, int type, vx_resc_t * indices)
{
    int pdim = points->count / npoints;
    int tdim = texcoords->count / npoints; //XXX Does this always need to be 2?

    vx_program_t * program = vx_program_load_library("texture");

    vx_program_set_vertex_attrib(program, "position", points, pdim);
    vx_program_set_vertex_attrib(program, "texIn", texcoords, tdim);
    vx_program_set_texture(program, "texture", tex,  width, height, format);
    vx_program_set_element_array(program, indices, type);
    return program->super;
}



vx_object_t * vxp_image(vx_resc_t * tex, int width, int height, int format)
{
    const int npoints = 4;

    float data[] = { 0.0f, 0.0f,
                     (float)width, 0.0f,
                     (float)width, (float) height,
                     0.0f,  (float)height};

    float texcoords[] = { 0.0f, 0.0f,
                          1.0f, 0.0f,
                          1.0f, 1.0f,
                          0.0f, 1.0f};

    int nidxs = 6;
    uint32_t idxs_tri[] = {0,1,2,
                           2,3,0};


    return vxp_texture(npoints, vx_resc_copyf(data, npoints*2), vx_resc_copyf(texcoords, npoints*2),
                       tex,  width, height, format,
                       GL_TRIANGLES, vx_resc_copyui(idxs_tri, nidxs));
}
