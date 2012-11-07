#ifndef __VX_H__
#define __VX_H__

#include <stdint.h>

int vx_init();

int fbo_create(int width, int height);

int paint_buffer(char * name, vx_obj_opcodes_t *voo);
int read_pixels_bgr(int width, int height, uint8_t *out_buf);





#endif
