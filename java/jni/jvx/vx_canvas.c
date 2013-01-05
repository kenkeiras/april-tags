#include "vx_canvas.h"

#include <stdlib.h>
#include "gtkuimagepane.h"
#include "GL/gl.h"

struct vx_canvas
{
    GtkuImagePane * imagePane;
    vx_local_renderer_t * lrend;
    int target_frame_rate;
    pthread_t thread;
};


void* vx_canvas_run(void *arg); // forward ref


static gboolean
on_key (GtkWidget *widget, GdkEventKey *event, gpointer release)
{
    printf("Canvas Key was %s\n",event->string);

    return TRUE;
}

vx_canvas_t * vx_canvas_create(vx_local_renderer_t * lrend)
{
    vx_canvas_t * vc = calloc(1, sizeof(vx_canvas_t));
    vc->imagePane = GTKU_IMAGE_PANE(gtku_image_pane_new());
    vc->lrend = lrend;

    // Connect signals:
    g_signal_connect (G_OBJECT (vc->imagePane), "key_press_event", G_CALLBACK (on_key), NULL);
    g_signal_connect (G_OBJECT (vc->imagePane), "key_release_event", G_CALLBACK (on_key), (gpointer)1);


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

        int width = gtku_image_pane_get_width(vc->imagePane);
        int height = gtku_image_pane_get_height(vc->imagePane);

        uint8_t data[width*height*3];

        vc->lrend->render(vc->lrend, width, height, data, GL_RGB);

        GdkPixbuf * pixbuf = gdk_pixbuf_new_from_data(data, GDK_COLORSPACE_RGB, FALSE, 8, width, height, width*3, NULL, NULL);


        // pixbuf is now managed by image pane
        gtku_image_pane_set_buffer(vc->imagePane, pixbuf);
    }
    pthread_exit(NULL);
}
