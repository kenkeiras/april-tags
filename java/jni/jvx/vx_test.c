#include <stdio.h>
#include <stdlib.h>
#include <GL/gl.h>

#include "vx_layer.h"
#include "vx_world.h"
#include "vx_program.h"

#include "vx_local_renderer.h"

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

// C version of the test code
int main(int argc, char ** args)
{
    if (argc < 1) {
        printf("Usage: ./vx_test <output.pnm>");
        return 1;
    }

    vx_local_initialize();

    int width = 640, height = 480;
    vx_local_renderer_t *lrend =vx_create_local_renderer(width, height);
    vx_renderer_t *rend = lrend->super;


    vx_world_t * world = vx_world_create(rend);
    vx_layer_t * layer = vx_layer_create(rend, world);

    const int npoints = 6;
    float data1[6*2] = { 1.0, 1.0,
                               0.0, 1.0,
                               0.0, 0.0,
                               0.0, 0.0,
                               1.0, 0.0,
                               1.0, 1.0};

    float colors1[6*3] = { 1.0, 0.0, 0.0,
                                 1.0, 0.0, 1.0,
                                 0.0, 1.0, 0.0,

                                 0.0, 1.0, 0.0,
                                 1.0, 1.0, 0.0,
                                 1.0, 0.0, 0.0};

    /* int nidxs = 6; */
    /* uint32_t idxs_tri[] = {0,1,2, */
    /*                        2,3,0}; */

    vx_program_t * program = vx_program_create(vx_resc_load("../../shaders/multi-colored.vert"),
                                               vx_resc_load("../../shaders/multi-colored.frag"));

    vx_program_set_vertex_attrib(program, "position", vx_resc_copy(data1, npoints), 2);
    vx_program_set_vertex_attrib(program, "color", vx_resc_copy(colors1, npoints), 3);
    vx_program_set_draw_array(program, 2, GL_TRIANGLES);


    vx_buffer_stage(vx_world_get_buffer(world, "foo"), program->super);
    vx_buffer_commit(vx_world_get_buffer(world, "foo"));

    uint8_t * data = calloc(width*height*3, sizeof(uint8_t));
    lrend->render(lrend, width, height, data);


    write_BGR(width, height, data, args[1]);
    return 0;
}
