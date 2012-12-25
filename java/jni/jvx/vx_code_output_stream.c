#include "vx_code_output_stream.h"
#include <stdlib.h>
#include <assert.h>
#include <endian.h>

// checks whether there is an additional 'remaining' bytes free past 'pos'
static void _ensure_space(vx_code_output_stream_t * codes, int remaining)
{
    int newlen = codes->len;

    while (remaining + codes->pos > newlen)
        newlen *= 2;

    if (newlen != codes->len) {
        codes->data = realloc(codes->data, newlen);
        codes->len = newlen;
    }
}

static void _write_uint32(vx_code_output_stream_t * codes, uint32_t val)
{
    _ensure_space(codes,sizeof(uint32_t));
    uint32_t * ptr = (uint32_t *)(codes->data+codes->pos);
    *ptr = htobe32(val);
    codes->pos+=sizeof(uint32_t);
}

static void _write_uint64(vx_code_output_stream_t * codes, uint64_t val)
{
    _ensure_space(codes,sizeof(uint64_t));
    uint64_t * ptr = (uint64_t *)(codes->data+codes->pos);
    *ptr = htobe64(val);

    codes->pos+=sizeof(uint64_t);
}

static void _write_float(vx_code_output_stream_t * codes, float val)
{
    // convert float bits to integer bits
    codes->write_uint32(codes, *((int *)(&val)));
}

static void _write_str (vx_code_output_stream_t * codes, char *  str)
{
    int slen = strlen(str);
    _ensure_space(codes,slen+1);
    memcpy(codes->data+codes->pos, str, slen+1);
    codes->pos+=slen+1;
}

vx_code_output_stream_t * vx_code_output_stream_create(int startlen)
{
    // all fields 0/NULL
    vx_code_output_stream_t * codes = calloc(sizeof(vx_code_output_stream_t), 1);
    codes->write_uint32 = _write_uint32;
    codes->write_uint64 = _write_uint64;
    codes->write_float = _write_float;
    codes->write_str = _write_str;

    assert(startlen != 0);
    // set initial allocation
    codes->len = startlen;
    codes->data = malloc(startlen);

    return codes;
}

void vx_code_output_stream_destroy(vx_code_output_stream_t * codes)
{
    free(codes->data);
    free(codes);
}
