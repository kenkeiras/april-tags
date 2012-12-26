#ifndef _IMAGE_U8_H
#define _IMAGE_U8_H

#include <stdint.h>

typedef struct image_u8 image_u8_t;
struct image_u8
{
    int width, height;
    int stride;
    int bpp; // How many bytes per pixel? 1 for GRAY, 3 for RGB, 4 for ARGB

    uint8_t *buf;
};

image_u8_t *image_u8_create(int width, int height, int bpp);
image_u8_t *image_u8_create_from_pnm(const char *path);
void image_u8_destroy(image_u8_t *im);

int image_u8_write_pnm(const image_u8_t *im, const char *path);

#endif
