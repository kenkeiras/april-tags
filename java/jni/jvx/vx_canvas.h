#ifndef __VX_CANVAS_H_
#define __VX_CANVAS_H_

#include <gtk/gtkwidget.h>
#include "vx_local_renderer.h"
#include "vx_layer.h"

typedef struct vx_canvas vx_canvas_t;

vx_canvas_t * vx_canvas_create(vx_local_renderer_t * rend);

void vx_canvas_add_layers(vx_canvas_t * vc, vx_layer_t * first, ...) __attribute__((sentinel));
vx_canvas_t * vx_canvas_create_varargs(vx_local_renderer_t * lrend, vx_layer_t * first, ...) __attribute__ ((sentinel));

GtkWidget * vx_canvas_get_gtk_widget(vx_canvas_t * vc);

#endif
