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

    void (* write_uint32)(vx_code_output_stream_t * stream, uint32_t val);
    void (* write_uint64)(vx_code_output_stream_t * stream, uint64_t val);
    void (* write_float)(vx_code_output_stream_t * stream, float val);
    void (* write_str)(vx_code_output_stream_t * stream, char *  str);
};

vx_code_output_stream_t * vx_code_output_stream_init(int startlen);
void vx_code_output_stream_destroy(vx_code_output_stream_t * stream);

#endif
