#ifndef __VX_H__
#define __VX_H__

#include <stdint.h>

#include "vx_resc.h"
#include "vx_code_input_stream.h"
int vx_initialize();
void vx_create();

int vx_update_buffer(char * name, vx_code_input_stream_t * codes);
int vx_update_resources(int nresc, vx_resc_t ** resources);
void vx_deallocate_resources(uint64_t * guids, int nguids);
int vx_set_system_pm_mat(float * pm);


int vx_render_read(int width, int height, uint8_t *out_buf);



#endif
