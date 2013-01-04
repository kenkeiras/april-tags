#include "vx_canvas.h"

#include <stdlib.h>


struct vx_canvas
{
    GtkWidget * foo;
};

vx_canvas_t * vx_canvas_create()
{
    vx_canvas_t * vc = calloc(1, sizeof(vx_canvas_t));
    return vc;
}

GtkWidget * vx_canvas_get_gtk_widget(vx_canvas_t * vc)
{
    return vc->foo;
}
