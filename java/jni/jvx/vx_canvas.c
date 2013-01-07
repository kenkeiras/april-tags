#include "vx_canvas.h"

#include <stdlib.h>
#include "gtkuimagepane.h"
#include "GL/gl.h"
#include "vx_event.h"
#include "vhash.h"
#include "varray.h"
#include "sort_util.h"


typedef struct
{
    varray_t * layers;
    vhash_t * camera_positions; //<int, vx_camera_pos_t>

    int width, height;
} render_info_t;

struct vx_canvas
{
    GtkuImagePane * imagePane;
    vx_local_renderer_t * lrend;
    int target_frame_rate;
    pthread_t thread;

    varray_t * layers; // XXX todo

    // for event handling:
    int button_mask;

    // From the last render event
    render_info_t * last_render_info;
    vx_mouse_event_t * last_mouse_event;
    vx_layer_t * mouse_pressed_layer;
};


void* vx_canvas_run(void *arg); // forward ref
int vx_canvas_dispatch_mouse(vx_canvas_t* vc, vx_mouse_event_t * event);
int vx_canvas_dispatch_key(vx_canvas_t* vc, vx_key_event_t * event);
void vx_canvas_update_button_states(vx_canvas_t* vc, int button_id, int value);

// Convert GTK modifiers to VX modifiers
static int gtk_to_vx_modifiers(int state)
{
    int modifiers = 0;

    // old, new
    int remap_bit[7][2] = {{0,0},
                           {1,4},
                           {2,1},
                           {3,3},
                           {4,5},
                           {5,-1},
                           {6,2}};

    for (int i = 0; i < 7; i++) {
        int outi = remap_bit[i][1];

        if (outi >= 0 && state >> i & 1)
            modifiers |= 1 << outi;
    }
    return modifiers;
}

// Convert GTK modifiers to VX key codes
static int gtk_to_vx_keycode(int gtk_code)
{
    // XXX These codes need to be fixed/converted
    return gtk_code;
}


// GTK event handlers
static gboolean
gtk_key_press (GtkWidget *widget, GdkEventKey *event, gpointer user)
{
    vx_canvas_t * vc = (vx_canvas_t*)user;
    vx_key_event_t key;
    key.modifiers = gtk_to_vx_modifiers(event->state);
    key.key_code = gtk_to_vx_keycode(event->keyval);
    key.released = 0;

    return vx_canvas_dispatch_key(vc, &key);
}

static gboolean
gtk_key_release (GtkWidget *widget, GdkEventKey *event, gpointer user)
{
    vx_canvas_t * vc = (vx_canvas_t*)user;
    vx_key_event_t key;
    key.modifiers = gtk_to_vx_modifiers(event->state);
    key.key_code = gtk_to_vx_keycode(event->keyval);
    key.released = 1;

    return vx_canvas_dispatch_key(vc, &key);
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
    vxe.modifiers = gtk_to_vx_modifiers(event->state);

    return vx_canvas_dispatch_mouse(vc, &vxe);
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
    vxe.modifiers = gtk_to_vx_modifiers(event->state);

    return vx_canvas_dispatch_mouse(vc, &vxe);
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
    vxe.modifiers = gtk_to_vx_modifiers(event->state);

    return vx_canvas_dispatch_mouse(vc, &vxe);
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
    vxe.modifiers = gtk_to_vx_modifiers(event->state);

    return vx_canvas_dispatch_mouse(vc, &vxe);
}




static  render_info_t * render_info_create()
{
    render_info_t * rinfo = calloc(1, sizeof(render_info_t));
    rinfo-> layers = varray_create();
    rinfo-> camera_positions = vhash_create(vhash_uint32_hash, vhash_uint32_equals);
    return rinfo;
}

static void  render_info_destroy(render_info_t * rinfo)
{
    // XXX Layer memory management
    // layers are stored elsewhere, so we don't need to free them;
    varray_destroy(rinfo->layers);
    vhash_map2(rinfo->camera_positions, NULL, vx_camera_pos_destroy);
    vhash_destroy(rinfo->camera_positions);
    free(rinfo);
}

static void _add_layers_real(vx_canvas_t * vc, vx_layer_t * first, va_list va)
{
    for (vx_layer_t * vl = first; vl != NULL; vl = va_arg(va, vx_layer_t *))
        varray_add(vc->layers, vl);
}

void vx_canvas_add_layers(vx_canvas_t * vc, vx_layer_t * first, ...)
{
    va_list va;
    va_start(va, first);
    _add_layers_real(vc, first, va);
    va_end(va);

}

vx_canvas_t * vx_canvas_create_varargs(vx_local_renderer_t * lrend, vx_layer_t * first, ...)
{
    vx_canvas_t * vc =  vx_canvas_create(lrend);
    va_list va;
    va_start(va, first);
    _add_layers_real(vc, first, va);
    va_end(va);
    return vc;
}

vx_canvas_t * vx_canvas_create(vx_local_renderer_t * lrend)
{
    vx_canvas_t * vc = calloc(1, sizeof(vx_canvas_t));
    vc->imagePane = GTKU_IMAGE_PANE(gtku_image_pane_new());
    vc->lrend = lrend;
    vc->layers = varray_create();

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

        // process all the layers
        render_info_t * rinfo = render_info_create();
        rinfo->width = width;
        rinfo->height = height;
        varray_add_all(rinfo->layers, vc->layers);
        // Now sort the layers based on draw order
        varray_sort(rinfo->layers, vx_layer_comparator);
        // iterate through each layer, compute absolute viewports and get camera positions
        for (int i = 0; i < varray_size(rinfo->layers); i++){
            vx_layer_t * vl = varray_get(rinfo->layers, i);
            vx_camera_mgr_t * mgr = vx_layer_camera_mgr(vl);

            uint64_t prev_id = -1; // XXX vhash bug
            void * prev_p = NULL;
            int * viewport = vx_layer_viewport_abs(vl, width, height);
            // store camera position, we need to eventually free the position
            uint64_t mtime = 0; // XXX
            vhash_put(rinfo->camera_positions, (void *)vx_layer_id(vl), mgr->get_camera_pos(mgr, viewport, mtime), &prev_id, &prev_p);
            assert(prev_p == NULL);
        }

        vc->lrend->render(vc->lrend, width, height, data, GL_RGB);

        GdkPixbuf * pixbuf = gdk_pixbuf_new_from_data(data, GDK_COLORSPACE_RGB, FALSE, 8, width, height, width*3, NULL, NULL);

        // pixbuf is now managed by image pane
        gtku_image_pane_set_buffer(vc->imagePane, pixbuf);


        render_info_t * old = vc->last_render_info;
        vc->last_render_info = rinfo;
        if (old)
            render_info_destroy(old); // XXX Threading?
    }
    pthread_exit(NULL);
}


/*
static void print_modifiers(uint32_t modifiers)
{
    if (modifiers & VX_SHIFT_MASK)
        printf("SHIFT\n");
    if (modifiers & VX_CTRL_MASK)
        printf("CTRL\n");
    if (modifiers & VX_WIN_MASK)
        printf("WIN\n");
    if (modifiers & VX_ALT_MASK)
        printf("ALT\n");
    if (modifiers & VX_CAPS_MASK)
        printf("CAPS\n");
    if (modifiers & VX_NUM_MASK)
        printf("NUM\n");
}
*/

static int check_viewport_bounds(double * xy, int * viewport)
{
    return (xy[0] >= viewport[0] &&
            xy[1] >= viewport[1] &&
            xy[0] < viewport[0] + viewport[2] &&
            xy[1] < viewport[1] + viewport[3]);
}

int vx_canvas_dispatch_mouse(vx_canvas_t* vc, vx_mouse_event_t * event)
{
    /* printf("mouse_event (%f,%f) buttons = %x scroll = %d modifiers = %x\n", */
    /*        event->xy[0],event->xy[1], event->button_mask, event->scroll_amt, event->modifiers); */
    int handled = 0;
    render_info_t * rinfo = vc->last_render_info; // XXX This reference is not safe (threading)
    if (rinfo == NULL)
        return handled;

    {// Make a copy of the event, whose memory we will manage
        vx_mouse_event_t * tmp = event;
        event  = malloc(sizeof(vx_mouse_event_t));
        memcpy(event, tmp, sizeof(vx_mouse_event_t));
    }

    vx_mouse_event_t * last_event = vc->last_mouse_event; // XXX Memory management!!! (stack allocated in other function)
    vc->last_mouse_event = event;
    if (!last_event) //XXX what to do here? (first time)
        return handled;

    event->xy[1] = rinfo->height - event->xy[1]; // compensate for flip??? XXX GTK? XXX Make sure this gets stored in last_mouse

    // check whether the mouse is dragging, or was released. These go to the last layer that got a mouse even
    uint32_t bdiff = event->button_mask ^ last_event->button_mask;

    // done with last event, lets free:
    free(last_event);

    // Either mouse motion while moving and buttons are down
    // or release event, where diff&buttons is all zeros
    // (i.e. the locations where buttons changed resulted in 0 in the new event buttons)
    if (event->button_mask || (bdiff && !(bdiff & event->button_mask) )) { // Check if there was a change, and if so, ensure it was
        if (vc->mouse_pressed_layer != NULL) {
            vx_camera_pos_t * pos = vhash_get(rinfo->camera_positions, (void *)vx_layer_id(vc->mouse_pressed_layer));
            if (pos) {
                handled = vx_layer_dispatch_mouse(vc->mouse_pressed_layer, pos, event);
                vx_camera_pos_destroy(pos);
            }
        }
        return handled; // guarantees that layers will get a mouse down before a drag, or mouse up
    }

    // Otherwise, we need to find a layer for this event
    for (int lidx = varray_size(rinfo->layers) -1; lidx >= 0; lidx--) {
        vx_layer_t * vl = varray_get(rinfo->layers, lidx);

        vx_camera_pos_t * pos =  vhash_get(rinfo->camera_positions, (void *)vx_layer_id(vl));

        // bounds check
        if (check_viewport_bounds(event->xy, pos->viewport)) {
            int handled  = vx_layer_dispatch_mouse(vl, pos, event);

            // On Mouse down, store which layer got the mouse down event
            if (bdiff && (bdiff & event->button_mask))
                vc->mouse_pressed_layer = handled ? vl : NULL;

            if (handled)
                return handled;
        }
    }
    return handled;
}

int vx_canvas_dispatch_key(vx_canvas_t* vc, vx_key_event_t * event)
{
    render_info_t * rinfo = vc->last_render_info; // XXX This reference is not safe (threading)
    int handled = 0;

    if (rinfo == NULL)
        return handled;

    double last_xy[2]  = {0.0, 0.0};

    vx_mouse_event_t * mouse = vc->last_mouse_event;
    if (mouse != NULL) {
        last_xy[0] = mouse->xy[0];
        last_xy[1] = mouse->xy[1];
    }

    // Otherwise, we need to find a layer for this event
    for (int lidx = varray_size(rinfo->layers) -1; lidx >= 0; lidx--) {
        vx_layer_t * vl = varray_get(rinfo->layers, lidx);
        vx_camera_pos_t * pos =  vhash_get(rinfo->camera_positions, (void *)vx_layer_id(vl));

        if (check_viewport_bounds(last_xy, pos->viewport)) {
            handled  = vx_layer_dispatch_key(vl, event);

            if (handled)
                return 1;
        }
    }

    return 0;
}


void vx_canvas_update_button_states(vx_canvas_t* vc, int button_id, int value)
{
    if (value) // button is down
        vc->button_mask |= 1 << button_id;
    else // button is up
        vc->button_mask &= ~(1 << button_id);
}


