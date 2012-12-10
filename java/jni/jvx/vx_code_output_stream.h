#ifndef __VX_CODE_OUTPUT_STREAM_H__
#define __VX_CODE_OUTPUT_STREAM_H__
#include <stdint.h>
#include <string.h>

typedef struct vx_code_output_stream vx_code_output_stream_t;

struct vx_code_output_stream
{
    const uint8_t * data;
    uint32_t pos;
    uint32_t len;

    uint32_t (* write_uint32)(vx_code_output_stream_t * stream);
    uint64_t (* write_uint64)(vx_code_output_stream_t * stream);
    float (* write_float)(vx_code_output_stream_t * stream);
    char * (* write_str)(vx_code_output_stream_t * stream);
    void (* reset)(vx_code_output_stream_t * stream);
};

// Copies the data
vx_code_output_stream_t * vx_code_output_stream_init(uint8_t *data, uint32_t codes_len);
void vx_code_output_stream_destroy(vx_code_output_stream_t * stream);

#endif
