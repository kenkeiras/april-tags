#include <stdlib.h>
#include <stdio.h>
#include <assert.h>

#include "image_u8.h"

#define ALIGNMENT 16

image_u8_t *image_u8_create(int width, int height, int bpp)
{
    image_u8_t *im = (image_u8_t*) calloc(1, sizeof(image_u8_t));

    im->width = width;
    im->height = height;
    im->bpp = bpp;
    im->stride = width*bpp;

    if ((im->stride % ALIGNMENT) != 0)
        im->stride += ALIGNMENT - (im->stride % ALIGNMENT);

    im->buf = (uint8_t*) calloc(1, im->height*im->stride);

    return im;
}

void image_u8_destroy(image_u8_t *im)
{
    free(im->buf);
    free(im);
}

// TODO Refactor this to load u32 and convert to u8 using existing function
image_u8_t *image_u8_create_from_pnm(const char *path)
{
    int width, height, format;
    image_u8_t *im = NULL;
    uint8_t *buf = NULL;

    FILE *f = fopen(path, "rb");
    if (f == NULL)
        return NULL;

    if (3 != fscanf(f, "P%d\n%d %d\n255", &format, &width, &height))
        goto error;

    // Binary Gray
    if (format == 5) {
        im = image_u8_create(width, height, 1);
    }
    // Binary RGB
    else if (format == 6) {
        im = image_u8_create(width, height, 3);
    }

    // Dump one character, new line after 255
    fread(im->buf, 1, 1, f);
    int sz = im->stride*height;
    if (sz != fread(im->buf, 1, sz, f))
        goto error;

    fclose(f);
    return im;

error:
    fclose(f);
    printf("Failed to read image\n");
    if (im != NULL)
        image_u8_destroy(im);

    if (buf != NULL)
        free(buf);

    return NULL;
}

int image_u8_write_pnm(const image_u8_t *im, const char *path)
{
    FILE *f = fopen(path, "wb");
    int res = 0;

    if (f == NULL) {
        res = -1;
        goto finish;
    }

    fprintf(f, "P5\n%d %d\n255\n", im->width, im->height);

    for (int y = 0; y < im->height; y++) {
        if (im->width != fwrite(&im->buf[y*im->stride], 1, im->width, f)) {
            res = -2;
            goto finish;
        }
    }

finish:
    if (f != NULL)
        fclose(f);

    return res;
}
