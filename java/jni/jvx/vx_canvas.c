#include "vx_canvas.h"

#include <stdlib.h>
#include "gtkuimagepane.h"
#include "GL/gl.h"
#include "vx_event.h"

struct vx_canvas
{
    GtkuImagePane * imagePane;
    vx_local_renderer_t * lrend;
    int target_frame_rate;
    pthread_t thread;

    varray_t * layers; // XXX todo

    // for event handling:
    int button_mask;
};


void* vx_canvas_run(void *arg); // forward ref
void vx_canvas_dispatch_mouse(vx_canvas_t* vc, vx_mouse_event_t * event);
void vx_canvas_dispatch_key(vx_canvas_t* vc, vx_key_event_t * event);
void vx_canvas_update_button_states(vx_canvas_t* vc, int button_id, int value);

/* static int gtk_to_vx_modifiers(int state) */
/* { */
/*     int modifiers = 0; */

/*     // old, new */
/*     int remap_bit[][] = {{0,0}, */
/*                          {1,4}, */
/*                          {2,1}, */
/*                          {6,2}, */
/*                          {3,3}, */
/*                          {4,5}}; */
/* } */


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
    vx_canvas_t * vc = (vx_canvas_t*)user;
    vx_mouse_event_t vxe; // Allocate on stack
    vxe.xy[0] = event->x;
    vxe.xy[1] = event->y;
    vxe.button_mask = vc->button_mask;
    vxe.scroll_amt = 0;
    vxe.modifiers = event->state;

    vx_canvas_dispatch_mouse(vc, &vxe);

    return TRUE;
}

static gboolean
gtk_button_press (GtkWidget *widget, GdkEventButton *event, gpointer user)
{
    vx_canvas_t * vc = (vx_canvas_t*)user;
    vx_canvas_update_button_states(vc, event->button, 1);

    vx_mouse_event_t vxe; // Allocate on stack
    vxe.xy[0] = event->x;
    vxe.xy[1] = event->y;
    vxe.button_mask = vc->button_mask;
    vxe.scroll_amt = 0;
    vxe.modifiers = event->state;

    vx_canvas_dispatch_mouse(vc, &vxe);

    return TRUE;
}

static gboolean
gtk_button_release (GtkWidget *widget, GdkEventButton *event, gpointer user)
{
    vx_canvas_t * vc = (vx_canvas_t*)user;
    vx_canvas_update_button_states(vc, event->button, 0);

    vx_mouse_event_t vxe; // Allocate on stack
    vxe.xy[0] = event->x;
    vxe.xy[1] = event->y;
    vxe.button_mask = vc->button_mask;
    vxe.scroll_amt = 0;
    vxe.modifiers = event->state;

    vx_canvas_dispatch_mouse(vc, &vxe);

    return TRUE;
}

static gboolean
gtk_scroll (GtkWidget *widget, GdkEventScroll *event, gpointer user)
{
    vx_canvas_t * vc = (vx_canvas_t*)user;

    vx_mouse_event_t vxe; // Allocate on stack
    vxe.xy[0] = event->x;
    vxe.xy[1] = event->y;
    vxe.button_mask = vc->button_mask;
    switch(event->direction) {
        case GDK_SCROLL_UP:
        case GDK_SCROLL_LEFT:
            vxe.scroll_amt = -1;
            break;
        case GDK_SCROLL_DOWN:
        case GDK_SCROLL_RIGHT: // mapping right scrolling -- kind of hacky. could ignore?
            vxe.scroll_amt = 1;
            break;
    }
    vxe.modifiers = event->state;

    vx_canvas_dispatch_mouse(vc, &vxe);


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

void vx_canvas_dispatch_mouse(vx_canvas_t* vc, vx_mouse_event_t * event)
{
    printf("mouse_event (%f,%f) buttons = %x scroll = %d modifiers = %x type = %d\n",
           event->xy[0],event->xy[1], event->button_mask, event->scroll_amt, event->modifiers, event->type);
}
void vx_canvas_dispatch_key(vx_canvas_t* vc, vx_key_event_t * event)
{

}

void vx_canvas_update_button_states(vx_canvas_t* vc, int button_id, int value)
{
    if (value) // button is down
        vc->button_mask |= 1 << button_id;
    else // button is up
        vc->button_mask &= ~(1 << button_id);
}
