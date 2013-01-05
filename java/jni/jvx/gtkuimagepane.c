#include "gtkuimagepane.h"


#define GTKU_IMAGE_PANE_GET_PRIVATE(o) (G_TYPE_INSTANCE_GET_PRIVATE ((o), GTKU_TYPE_IMAGE_PANE, GtkuImagePanePrivate))
typedef struct _GtkuImagePanePrivate GtkuImagePanePrivate;
struct _GtkuImagePanePrivate {
    int width, height;
    GdkPixmap *pixMap; // server side off screen map
    GdkPixbuf *pixBuf; // client side image resource, can be NULL
};

static gboolean gtku_image_pane_expose(GtkWidget * widget, GdkEventExpose *expose);
static void gtku_image_pane_realize (GtkWidget * widget);
static void gtku_image_pane_unrealize (GtkWidget * widget);
static void gtku_image_pane_size_allocate (GtkWidget * widget,
        GtkAllocation * allocation);

G_DEFINE_TYPE (GtkuImagePane, gtku_image_pane, GTK_TYPE_DRAWING_AREA);

static void
gtku_image_pane_class_init (GtkuImagePaneClass * klass)
{
    GtkWidgetClass * widget_class = GTK_WIDGET_CLASS (klass);
    GObjectClass * gobject_class = G_OBJECT_CLASS (klass);

    widget_class->expose_event = gtku_image_pane_expose;
    widget_class->realize = gtku_image_pane_realize;
    widget_class->unrealize = gtku_image_pane_unrealize;
    widget_class->size_allocate = gtku_image_pane_size_allocate;

    g_type_class_add_private (gobject_class, sizeof (GtkuImagePanePrivate));
}

static void
gtku_image_pane_init (GtkuImagePane *self)
{
    GtkuImagePanePrivate * priv = GTKU_IMAGE_PANE_GET_PRIVATE (self);
    priv->width = 0;
    priv->height = 0;
    priv->pixMap = NULL;
    priv->pixBuf = NULL;

    gtk_widget_add_events(GTK_WIDGET(self), GDK_ALL_EVENTS_MASK);
}


GtkWidget *
gtku_image_pane_new ()
{
    return GTK_WIDGET (g_object_new (GTKU_TYPE_IMAGE_PANE, NULL));
}


static void
gtku_image_pane_realize (GtkWidget * widget)
{
    /* chain up */
    GTK_WIDGET_CLASS (gtku_image_pane_parent_class)->realize (widget);
}

static void
gtku_image_pane_unrealize (GtkWidget * widget)
{
    /* chain up */
    GTK_WIDGET_CLASS (gtku_image_pane_parent_class)->unrealize (widget);
}


static void
gtku_image_pane_size_allocate (GtkWidget * widget,
                               GtkAllocation * allocation)
{
    GtkuImagePane * self = GTKU_IMAGE_PANE (widget);
    GtkuImagePanePrivate * priv = GTKU_IMAGE_PANE_GET_PRIVATE (self);

    /* chain up */
    GTK_WIDGET_CLASS (gtku_image_pane_parent_class)->size_allocate (widget,
            allocation);

    priv->width = allocation->width;
    priv->height = allocation->height;

    // create a new backing drawable, which is the correct size
    GdkPixmap * newMap = gdk_pixmap_new(widget->window,
                                        priv->width,
                                        priv->height,
                                        -1);

    // Render the latest image, if we've gotten it:
    if (priv->pixBuf) {
        gdk_draw_pixbuf(newMap,
                        NULL, // graphics context, for clipping?
                        priv->pixBuf,
                        0,0, // source in pixbuf
                        0,0, // destination in pixmap
                        -1,-1, // use full width and height
                        GDK_RGB_DITHER_NONE, 0,0);

        // Now also render where the image is not:
        gdk_draw_rectangle (newMap,
                            widget->style->black_gc,
                            TRUE,
                            0, gdk_pixbuf_get_height(priv->pixBuf),
                            widget->allocation.width,
                            widget->allocation.height- gdk_pixbuf_get_height(priv->pixBuf));

        gdk_draw_rectangle (newMap,
                            widget->style->black_gc,
                            TRUE,
                            gdk_pixbuf_get_width(priv->pixBuf), 0,
                            widget->allocation.width - gdk_pixbuf_get_width(priv->pixBuf),
                            gdk_pixbuf_get_height(priv->pixBuf));

    } else {
        // write some initial data in there
        gdk_draw_rectangle (newMap,
                            widget->style->black_gc,
                            TRUE,
                            0, 0,
                            widget->allocation.width,
                            widget->allocation.height);
    }

    GdkPixmap * oldMap = priv->pixMap;
    priv->pixMap = newMap;
    if (oldMap) {
        g_object_unref(oldMap);
    }
}

/* Redraw the screen from the backing pixmap */
static gboolean
gtku_image_pane_expose(GtkWidget *widget, GdkEventExpose *event )
{
    GtkuImagePane * self = GTKU_IMAGE_PANE (widget);
    GtkuImagePanePrivate * priv = GTKU_IMAGE_PANE_GET_PRIVATE (self);

    gdk_draw_drawable(GDK_DRAWABLE(gtk_widget_get_window(widget)),
                      widget->style->fg_gc[GTK_WIDGET_STATE (widget)],
                      priv->pixMap,
                      event->area.x, event->area.y,
                      event->area.x, event->area.y,
                      event->area.width, event->area.height);

  return FALSE;
}


void gtku_image_pane_set_buffer (GtkuImagePane * self, GdkPixbuf * pixbuf)
{
    /* GtkuImagePane * self = GTKU_IMAGE_PANE (widget); */
    GtkWidget * widget = GTK_WIDGET(self);
    GtkuImagePanePrivate * priv = GTKU_IMAGE_PANE_GET_PRIVATE (self);


    // Draw the image onto the backing pixbuf,
    // then tell GDK it needs to redraw the whole window:

    GdkPixbuf * oldpb = priv->pixBuf;
    //1
    priv->pixBuf = pixbuf;
    if (oldpb)
        g_object_unref(oldpb);

    GdkPixmap * drawable = priv->pixMap;

    if (priv->pixMap == NULL)
        return; // Nothing to do

    gdk_threads_enter();

    gdk_draw_pixbuf(drawable,
                    NULL, // graphics context, for clipping?
                    priv->pixBuf,
                    0,0, // source in pixbuf
                    0,0, // destination in pixmap
                    -1,-1, // use full width and height
                    GDK_RGB_DITHER_NONE, 0,0);

    //2
    gtk_widget_queue_draw_area (widget,
                                0, 0,
                                priv->width, priv->height);

    gdk_threads_leave();

}

int gtku_image_pane_get_width (GtkuImagePane * imgPane)
{
    return GTKU_IMAGE_PANE_GET_PRIVATE(imgPane)->width;
}

int gtku_image_pane_get_height (GtkuImagePane * imgPane)
{
    return GTKU_IMAGE_PANE_GET_PRIVATE(imgPane)->height;
}
