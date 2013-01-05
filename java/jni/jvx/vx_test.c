#include <stdio.h>
#include <stdlib.h>
#include <GL/gl.h>
#include <assert.h>

#include "vx_layer.h"
#include "vx_world.h"
#include "vx_program.h"
#include "vx_points.h"
#include "vx_lines.h"
#include "vx_chain.h"

#include "vxp.h"

#include "vx_local_renderer.h"
#include "image_u8.h"
#include "vx_canvas.h"
#include <gtk/gtk.h>

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

vx_object_t * make_square()
{
    const int npoints = 4;

    float data1[] = { 0.0f, 0.0f,
                      -1.0f, 0.0f,
                      -1.0f, -1.0f,
                      0.0f, -1.0f};

    float colors1[] = { 1.0f, 0.0f, 0.0f,
                        1.0f, 0.0f, 1.0f,
                        0.0f, 1.0f, 0.0f,
                        1.0f, 1.0f, 0.0f};

    int nidxs = 6;
    uint32_t idxs_tri[] = {0,1,2,
                           2,3,0};

    return vxp_multi_colored_indexed(npoints, vx_resc_copyf(data1, npoints*2), vx_resc_copyf(colors1, npoints*3), 1.0, GL_TRIANGLES, vx_resc_copyui(idxs_tri, nidxs));
}

static vx_object_t * make_tex(image_u8_t * img)
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

    return vxp_texture(npoints, vx_resc_copyf(data, npoints*2), vx_resc_copyf(texcoords, npoints*2),
                       vx_resc_copyub(img->buf, img->width*img->height*3),  img->width, img->height, GL_RGB,
                       GL_TRIANGLES, vx_resc_copyui(idxs_tri, nidxs));
}

static double runif()
{
    return (double)rand() / (double)RAND_MAX;
}

// C version of the test code
int main(int argc, char ** args)
{
    if (argc < 2) {
        printf("Usage: ./vx_test <texture.pnm>\n");// <output.pnm>\n");
        return 1;
    }
    image_u8_t * img = image_u8_create_from_pnm(args[1]);
    assert(img);

    vx_local_initialize();
    vx_program_library_init();

    int width = 640, height = 480;
    vx_local_renderer_t *lrend =vx_create_local_renderer(width, height);
    vx_renderer_t *rend = lrend->super;


    vx_world_t * world = vx_world_create(rend);
    vx_layer_t * layer = vx_layer_create(rend, world);

    vx_object_t * o1 = make_square();

    vx_object_t * o2 = make_tex(img);
    vx_object_t * o3 = vxp_image(vx_resc_copyub(img->buf, img->width*img->height*3),  img->width, img->height, GL_RGB);

    vx_object_inc_ref(o2);
    vx_object_inc_ref(o3); // XXX debug

    vx_object_t * vchain = vx_chain(o2,o3);
    vx_chain_add(vchain, o1);
    vx_buffer_stage(vx_world_get_buffer(world, "tex"), vchain);
    vx_buffer_commit(vx_world_get_buffer(world, "tex"));


    srand(9L);
    int nrp = 100;
    float red[] = {1.0,0.0,0.0,1.0};
    float rand_points[3*nrp];
    float rand_colors[3*nrp];
    for (int i = 0; i < nrp; i++) {
        rand_points[3*i + 0] = 2*runif() - 1;
        rand_points[3*i + 1] = 2*runif() - 1;
        rand_points[3*i + 2] = 2*runif() - 1;

        rand_colors[3*i + 0] = runif();
        rand_colors[3*i + 1] = runif();
        rand_colors[3*i + 2] = runif();

    }

    vx_buffer_set_draw_order(vx_world_get_buffer(world, "points"), 10);
    vx_buffer_stage(vx_world_get_buffer(world, "points"),
                    vxp_multi_colored(nrp, vx_resc_copyf(rand_points, nrp*3), vx_resc_copyf(rand_colors, nrp*3), 6.0, GL_POINTS));
    vx_buffer_commit(vx_world_get_buffer(world, "points"));

    vx_buffer_set_draw_order(vx_world_get_buffer(world, "lines"), 8);
    vx_buffer_stage(vx_world_get_buffer(world, "lines"),
                    vxp_single_color(nrp, vx_resc_copyf(rand_points, nrp*3), red, 3.0, GL_LINES));
    vx_buffer_commit(vx_world_get_buffer(world, "lines"));


    g_thread_init (NULL);
    gdk_threads_init ();
    gdk_threads_enter();
    gtk_init (&argc, &args);

    vx_canvas_t * vc = vx_canvas_create(lrend);


    GtkWidget * window = gtk_window_new (GTK_WINDOW_TOPLEVEL);
    GtkWidget * canvas = vx_canvas_get_gtk_widget(vc);
    gtk_window_set_default_size (GTK_WINDOW (window), 400, 400);
    gtk_container_add(GTK_CONTAINER(window), canvas);
    gtk_widget_show (window);
    gtk_widget_show (canvas); // XXX Show all causes errors!

    g_signal_connect_swapped(G_OBJECT(window), "destroy",
                             G_CALLBACK(gtk_main_quit), NULL);

    gtk_main();
    gdk_threads_leave();


    // cleanup:
    rend->destroy(rend);
    vx_world_destroy(world);
    vx_layer_destroy(layer);
    image_u8_destroy(img);

    return 0;
}
