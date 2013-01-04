#include "vx_canvas.h"

#include <stdlib.h>
#include "gtkimagepane.h"

struct vx_canvas
{
    GtkuImagePane * imagePane;
    vx_renderer_t * rend;
    int target_frame_rate;
    pthread_t thread;
};


void* vx_canvas_run(void *arg); // forward ref

vx_canvas_t * vx_canvas_create(vx_renderer_t * rend)
{
    vx_canvas_t * vc = calloc(1, sizeof(vx_canvas_t));
    vc->imagePane = GTKU_IMAGE_PANE(gtku_image_pane_new());
    vc->rend = rend;

    vc->target_frame_rate = 5;
    pthread_create(&vc->thread, NULL, vx_canvas_run, vc);

    // start rendering thread

    return vc;
}

GtkWidget * vx_canvas_get_gtk_widget(vx_canvas_t * vc)
{
    return GTK_WIDGET(vc->imagePane);
}


void* vx_canvas_run(void * arg)
{
    vx_canvas_t * vc = arg;

    while (1) {
        usleep(1000000 / vc->target_frame_rate);

    }
    pthread_exit(NULL);
}
