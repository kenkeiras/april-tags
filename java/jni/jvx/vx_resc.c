#include "GL/gl.h"
#include "vx_resc.h"
#include "vx_util.h"
#include <malloc.h>
#include <assert.h>

#define MAX_SHD_SZ 65355

void vx_resc_destroy(vx_resc_t * r)
{
    if (r->res != NULL)
        free(r->res);
    free(r);
}

vx_resc_t * vx_resc_load(char* path)
{
    vx_resc_t * vr = calloc(1, sizeof(vx_resc_t));
    vr->type = GL_BYTE;
    vr->id = vx_alloc_id();
    vr->fieldwidth = sizeof(char);
    {
        FILE *fp = fopen(path,"r");

        char * chars = calloc(MAX_SHD_SZ, sizeof(char));

        vr->count = fread(chars, sizeof(char), MAX_SHD_SZ, fp);
        assert(vr->count < MAX_SHD_SZ);
        vr->res = chars;
        fclose(fp);
    }
    return vr;
}
