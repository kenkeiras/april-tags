#include "vx_code_input_stream.h"
#include <malloc.h>
#include <assert.h>
#include <endian.h>

static void vx_ensure_space(vx_code_input_stream_t * stream, int size)
{
    assert(stream->len >= stream->pos + size);
}

uint32_t vx_read_uint32(vx_code_input_stream_t * stream)
{
    vx_ensure_space(stream, 4);

    uint32_t result =
        (((uint32_t)stream->data[stream->pos++]) << 24) |
        (((uint32_t)stream->data[stream->pos++]) << 16) |
        (((uint32_t)stream->data[stream->pos++]) << 8 ) |
        (((uint32_t)stream->data[stream->pos++]) << 0 );

    return be32toh(result);
}


uint64_t vx_read_uint64(vx_code_input_stream_t * stream)
{
    vx_ensure_space(stream, 8);

    uint64_t result =
        (((uint64_t)stream->data[stream->pos++]) << 56) |
        (((uint64_t)stream->data[stream->pos++]) << 48) |
        (((uint64_t)stream->data[stream->pos++]) << 40) |
        (((uint64_t)stream->data[stream->pos++]) << 32) |
        (((uint64_t)stream->data[stream->pos++]) << 24) |
        (((uint64_t)stream->data[stream->pos++]) << 16) |
        (((uint64_t)stream->data[stream->pos++]) << 8 ) |
        (((uint64_t)stream->data[stream->pos++]) << 0 );

    return be64toh(result);
}

// Returns a string which caller must free
char * vx_read_str(vx_code_input_stream_t * stream)
{
    int remaining  = stream->len - stream->pos;
    int str_size = strnlen(stream->data+stream->pos, remaining);
    assert(remaining != str_size);  // Ensure there's a '\0' terminator

    char * str = strdup(stream->data+stream->pos);
    stream->pos += str_size;

    return str;
}


vx_code_input_stream_t * vx_code_input_stream_init(uint8_t *data, uint32_t codes_len)
{
    vx_code_input_stream_t * stream = malloc(sizeof(vx_code_input_stream_t));
    printf("Initializing buffer: len %d\n", codes_len);
    stream->len = codes_len;
    printf("Initializing buffer: stream->len %d\n", stream->len);
    stream->data = malloc(sizeof(uint8_t)*stream->len);
    memcpy(stream->data, data, sizeof(uint8_t)*stream->len);
    stream->pos = 0;

    // Set function pointers
    stream->read_uint32 = vx_read_uint32;
    stream->read_uint64 = vx_read_uint64;
    stream->read_str = vx_read_str;
}


void vx_code_input_stream_destroy(vx_code_input_stream_t * stream)
{
    free(stream->data);
    free(stream);
}

