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

    uint32_t result = * ((uint32_t*)(stream->data + stream->pos));
    stream->pos += 4;

    return be32toh(result);
}


uint64_t vx_read_uint64(vx_code_input_stream_t * stream)
{
    vx_ensure_space(stream, 8);

    uint64_t result = * ((uint64_t*)(stream->data + stream->pos));
    stream->pos += 8;

    return be64toh(result);
}

// Returns a string which caller must free
char * vx_read_str(vx_code_input_stream_t * stream)
{
    uint32_t remaining  = stream->len - stream->pos;

    char * str_start = (char*)(stream->data+stream->pos);
    uint32_t str_size = strnlen(str_start, remaining);
    assert(remaining != str_size);  // Ensure there's a '\0' terminator

    char * str = strdup(str_start); //XXX Do we really need this copy?
    stream->pos += str_size + 1; // +1 to account for null terminator

    return str;
}


vx_code_input_stream_t * vx_code_input_stream_init(uint8_t *data, uint32_t codes_len)
{
    vx_code_input_stream_t * stream = malloc(sizeof(vx_code_input_stream_t));
    stream->len = codes_len;
    stream->pos = 0;

    stream->data = malloc(sizeof(uint8_t)*stream->len);
    memcpy(stream->data, data, sizeof(uint8_t)*stream->len);

    // Set function pointers
    stream->read_uint32 = vx_read_uint32;
    stream->read_uint64 = vx_read_uint64;
    stream->read_str = vx_read_str;

    return stream;
}


void vx_code_input_stream_destroy(vx_code_input_stream_t * stream)
{
    free(stream->data);
    free(stream);
}

