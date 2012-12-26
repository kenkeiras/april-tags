#include <stdio.h>
#include <stdlib.h>
#include <GL/gl.h>
#include <assert.h>

#include "vx_layer.h"
#include "vx_world.h"
#include "vx_program.h"

#include "vx_local_renderer.h"
#include "image_u8.h"

int write_BGR(int width, int height, const uint8_t *data, const char *path)
{
    FILE *f = fopen(path, "wb");
    int res = 0;

    if (f == NULL) {
        res = -1;
        goto finish;
    }

    fprintf(f, "P6\n%d %d\n255\n", width, height);

    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            fwrite(&data[3*(y*width +x) + 2], 1, 1, f); // reverse R,B
            fwrite(&data[3*(y*width +x) + 1], 1, 1, f);
            fwrite(&data[3*(y*width +x) + 0], 1, 1, f);
        }
    }

finish:
    if (f != NULL)
        fclose(f);

    return res;
}

vx_program_t * make_square()
{
    const int npoints = 4;

    float data1[] = { 0.0f, 0.0f,
                      -1.0f, 0.0f,
                      -1.0f, -1.0f,
                      0.0f, -1.0f};
    /* float data1[] = { 1.0f, 1.0f, */
    /*                   0.0f, 1.0f, */
    /*                   0.0f, 0.0f, */
    /*                   1.0f, 0.0f}; */

    float colors1[] = { 1.0f, 0.0f, 0.0f,
                        1.0f, 0.0f, 1.0f,
                        0.0f, 1.0f, 0.0f,
                        1.0f, 1.0f, 0.0f};

    int nidxs = 6;
    uint32_t idxs_tri[] = {0,1,2,
                           2,3,0};

    vx_program_t * program = vx_program_create(vx_resc_load("../../shaders/multi-colored.vert"),
                                               vx_resc_load("../../shaders/multi-colored.frag"));

    vx_program_set_vertex_attrib(program, "position", vx_resc_copyf(data1, npoints*2), 2);
    vx_program_set_vertex_attrib(program, "color", vx_resc_copyf(colors1, npoints*3), 3);
    /* vx_program_set_uniform4fv(program,"color", colors1); // just use the first one */
    /* vx_program_set_draw_array(program, 6, GL_TRIANGLES); */
    vx_program_set_element_array(program, vx_resc_copyui(idxs_tri, nidxs), GL_TRIANGLES);
    return program;
}

static vx_program_t * make_tex(image_u8_t * img)
{
    const int npoints = 4;

    float data[] = { 0.0f, -1.0f,
                     1.0f,  -1.0f,
                     1.0f, 0.0f,
                     0.0f,  0.0f};

    float texcoords[] = { 0.0f, 0.0f,
                          1.0f, 0.0f,
                          1.0f, 1.0f,
                          0.0f, 1.0f};

    int nidxs = 6;
    uint32_t idxs_tri[] = {0,1,2,
                           2,3,0};

    vx_program_t * program = vx_program_create(vx_resc_load("../../shaders/texture.vert"),
                                               vx_resc_load("../../shaders/texture.frag"));

    vx_program_set_vertex_attrib(program, "position", vx_resc_copyf(data, npoints*2), 2);
    vx_program_set_vertex_attrib(program, "texIn", vx_resc_copyf(texcoords, npoints*2), 2);
    vx_program_set_texture(program, "texture", vx_resc_copyub(img->buf, img->width*img->height*3),  img->width, img->height, GL_RGB);
    vx_program_set_element_array(program, vx_resc_copyui(idxs_tri, nidxs), GL_TRIANGLES);
    return program;
}

// C version of the test code
int main(int argc, char ** args)
{
    if (argc < 3) {
        printf("Usage: ./vx_test <texture.pnm> <output.pnm>\n");
        return 1;
    }
    image_u8_t * img = image_u8_create_from_pnm(args[1]);
    assert(img);

    vx_local_initialize();

    int width = 640, height = 480;
    vx_local_renderer_t *lrend =vx_create_local_renderer(width, height);
    vx_renderer_t *rend = lrend->super;


    vx_world_t * world = vx_world_create(rend);
    vx_layer_t * layer = vx_layer_create(rend, world);


    vx_program_t * program = make_square();
    vx_buffer_stage(vx_world_get_buffer(world, "foo"), program->super);
    vx_buffer_commit(vx_world_get_buffer(world, "foo"));

    vx_program_t * program2 = make_tex(img);
    vx_buffer_stage(vx_world_get_buffer(world, "img"), program2->super);
    vx_buffer_commit(vx_world_get_buffer(world, "img"));


    uint8_t * data = calloc(width*height*3, sizeof(uint8_t));
    lrend->render(lrend, width, height, data);


    write_BGR(width, height, data, args[2]);
    return 0;
}
