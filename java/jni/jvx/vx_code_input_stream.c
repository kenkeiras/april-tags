#include "vx_code_input_stream.h"
#include <malloc.h>
#include <assert.h>

static void vx_ensure_space(vx_code_input_stream_t * stream, int size)
{
    assert(stream->len >= stream->pos + size);
}

uint32_t vx_read_uint32(vx_code_input_stream_t * stream)
{
    vx_ensure_space(stream, 4);

    uint32_t result =
        ((uint32_t)(stream->data[stream->pos++]) << 24) |
        (((uint32_t)stream->data[stream->pos++]) << 16) |
        (((uint32_t)stream->data[stream->pos++]) << 8 )  |
        (((uint32_t)stream->data[stream->pos++]) << 0 );

    return result;
}


uint64_t vx_read_uint64(vx_code_input_stream_t * stream)
{
    vx_ensure_space(stream, 8);

    uint64_t result =
        (((uint64_t)stream->data[stream->pos++]) << 56) |
        ((uint64_t)(stream->data[stream->pos++]) << 48) |
        ((uint64_t)(stream->data[stream->pos++]) << 40) |
        (((uint64_t)stream->data[stream->pos++]) << 32) |
        ((uint64_t)(stream->data[stream->pos++]) << 24) |
        (((uint64_t)stream->data[stream->pos++]) << 16) |
        (((uint64_t)stream->data[stream->pos++]) << 8 )  |
        (((uint64_t)stream->data[stream->pos++]) << 0 );

    return result;
}


vx_code_input_stream_t * vx_code_input_stream_init(uint8_t *data, uint32_t codes_len)
{
    vx_code_input_stream_t * stream = malloc(sizeof(vx_code_input_stream_t));
    stream->len = codes_len;
    stream->data = malloc(sizeof(uint8_t)*stream->len);
    memcpy(stream->data,data, sizeof(uint8_t)*stream->len);
    stream->pos = 0;

    // Set function pointers
    stream->read_uint32 = vx_read_uint32;
}


void vx_code_input_stream_destroy(vx_code_input_stream_t * stream)
{
    free(stream->data);
    free(stream);
}

