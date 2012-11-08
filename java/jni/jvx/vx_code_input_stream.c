#include "vx_code_input_stream.h"
#include <malloc.h>

vx_code_input_stream_t * vx_code_input_stream_init(uint8_t *data, uint32_t codes_len)
{
    vx_code_input_stream_t * stream = malloc(sizeof(vx_code_input_stream_t));
    stream->len = codes_len;
    stream->data = malloc(sizeof(uint8_t)*stream->len);
    memcpy(stream->data,data, sizeof(uint8_t)*stream->len);
    stream->pos = 0;
}


void vx_code_input_stream_destroy(vx_code_input_stream_t * stream)
{
    free(stream->data);
    free(stream);
}

