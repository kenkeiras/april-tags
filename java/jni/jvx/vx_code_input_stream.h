#ifndef __VX_CODE_INPUT_STREAM_H__
#define __VX_CODE_INPUT_STREAM_H__
#include <stdint.h>
#include <string.h>

typedef struct vx_code_input_stream vx_code_input_stream_t;

struct vx_code_input_stream
{
    const uint8_t * data;
    uint32_t pos;
    uint32_t len;

    uint32_t (* read_uint32)(vx_code_input_stream_t * stream);
    uint64_t (* read_uint64)(vx_code_input_stream_t * stream);
    float (* read_float)(vx_code_input_stream_t * stream);
    char * (* read_str)(vx_code_input_stream_t * stream);
    void (* reset)(vx_code_input_stream_t * stream);
};

// Copies the data
vx_code_input_stream_t * vx_code_input_stream_init(uint8_t *data, uint32_t codes_len);
void vx_code_input_stream_destroy(vx_code_input_stream_t * stream);

#endif
