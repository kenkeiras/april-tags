#ifndef __VX_H__
#define __VX_H__

#include <stdint.h>

#include "vx_resc.h"
#include "vx_code_input_stream.h"
int vx_init();

int fbo_create(int width, int height);

int vx_update_buffer(char * name, vx_code_input_stream_t * codes);
int vx_update_resources(int nresc, vx_resc_t ** resources);
int read_pixels_bgr(int width, int height, uint8_t *out_buf);





#endif
