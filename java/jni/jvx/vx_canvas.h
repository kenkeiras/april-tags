#ifndef __VX_CANVAS_H_
#define __VX_CANVAS_H_

#include <gtk/gtkwidget.h>
#include "vx_renderer.h"

typedef struct vx_canvas vx_canvas_t;

vx_canvas_t * vx_canvas_create(vx_renderer_t * rend);

GtkWidget * vx_canvas_get_gtk_widget(vx_canvas_t * vc);

#endif
