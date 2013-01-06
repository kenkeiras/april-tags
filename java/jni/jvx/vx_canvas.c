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

// GTK event handlers
static gboolean
gtk_key_press (GtkWidget *widget, GdkEventKey *event, gpointer user)
{
    printf("key pressed %s\n",event->string);

    return TRUE;
}

static gboolean
gtk_key_release (GtkWidget *widget, GdkEventKey *event, gpointer user)
{
    printf("key release %s\n",event->string);

    return TRUE;
}

static gboolean
gtk_motion (GtkWidget *widget, GdkEventMotion *event, gpointer user)
{
    printf("motion\n");
    return TRUE;
}

static gboolean
gtk_button_press (GtkWidget *widget, GdkEventButton *event, gpointer user)
{
    printf("button press\n");
    return TRUE;
}

static gboolean
gtk_button_release (GtkWidget *widget, GdkEventButton *event, gpointer user)
{
    printf("button release\n");
    return TRUE;
}

static gboolean
gtk_scroll (GtkWidget *widget, GdkEventScroll *event, gpointer user)
{
    printf("scroll\n");
    return TRUE;
}

vx_canvas_t * vx_canvas_create(vx_local_renderer_t * lrend)
{
    vx_canvas_t * vc = calloc(1, sizeof(vx_canvas_t));
    vc->imagePane = GTKU_IMAGE_PANE(gtku_image_pane_new());
    vc->lrend = lrend;

    // Connect signals:
    g_signal_connect (G_OBJECT (vc->imagePane), "key_release_event",   G_CALLBACK (gtk_key_release),  vc);
    g_signal_connect (G_OBJECT (vc->imagePane), "key_press_event",     G_CALLBACK (gtk_key_press),    vc);
    g_signal_connect (G_OBJECT (vc->imagePane), "motion-notify-event", G_CALLBACK (gtk_motion),       vc);

    g_signal_connect (G_OBJECT (vc->imagePane), "button-press-event",  G_CALLBACK (gtk_button_press),   vc);
    g_signal_connect (G_OBJECT (vc->imagePane), "button-release-event",G_CALLBACK (gtk_button_release), vc);
    g_signal_connect (G_OBJECT (vc->imagePane), "scroll-event",        G_CALLBACK (gtk_scroll),         vc);

    // start rendering thread
    vc->target_frame_rate = 5;
    pthread_create(&vc->thread, NULL, vx_canvas_run, vc);

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
