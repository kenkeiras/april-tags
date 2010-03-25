#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/mman.h>
#include <asm/types.h>
#include <assert.h>
#include <inttypes.h>
#include <errno.h>
#include <stdint.h>

#include <dc1394/control.h>
#include <dc1394/vendor/avt.h>

#define IMAGE_SOURCE_UTILS
#include "image_source.h"

#define IMPL_TYPE 0x44431394

typedef struct impl_dc1394 impl_dc1394_t;
struct impl_dc1394
{
    int                   fd;

    dc1394_t              *dc1394;
    dc1394camera_t        *cam;

    int                   nformats;
    image_source_format_t **formats;
    int                   current_format_idx;

    int                   num_buffers;

    dc1394video_frame_t   *current_frame;
};


struct format_priv
{
    dc1394video_mode_t dc1394_mode;
    int format7_mode_idx;
    int color_coding_idx;
};

static const char *toformat(dc1394color_coding_t color, dc1394color_filter_t filter)
{
    switch (color) {
        case DC1394_COLOR_CODING_MONO8:
            return "GRAY8";
        case DC1394_COLOR_CODING_RAW8:
            switch (filter) {
                case DC1394_COLOR_FILTER_RGGB:
                    return "BAYER_RGGB";
                case DC1394_COLOR_FILTER_GBRG:
                    return "BAYER_GBRG";
                case DC1394_COLOR_FILTER_GRBG:
                    return "BAYER_GRBG";
                case DC1394_COLOR_FILTER_BGGR:
                    return "BAYER_BGGR";
                default:
                    return "GRAY";
            }
        case DC1394_COLOR_CODING_YUV411:
            return "YUV422";
        case DC1394_COLOR_CODING_YUV422:
            return "UYVY";
        case DC1394_COLOR_CODING_YUV444:
            return "IYU2";
        case DC1394_COLOR_CODING_RGB8:
            return "RGB";
        case DC1394_COLOR_CODING_MONO16:
            return "GRAY16";
        case DC1394_COLOR_CODING_RGB16:
            return "BE_RGB16";
        case DC1394_COLOR_CODING_MONO16S:
            return "BE_SIGNED_GRAY16";
        case DC1394_COLOR_CODING_RGB16S:
            return "BE_SIGNED_RGB16";
        case DC1394_COLOR_CODING_RAW16:
            return "BE_GRAY16";
    }
    return "UNKNOWN";
}

static int num_formats(image_source_t *isrc)
{
    assert(isrc->impl_type == IMPL_TYPE);
    impl_dc1394_t *impl = (impl_dc1394_t*) isrc->impl;

    return impl->nformats;
}

static image_source_format_t *get_format(image_source_t *isrc, int idx)
{
    assert(isrc->impl_type == IMPL_TYPE);
    impl_dc1394_t *impl = (impl_dc1394_t*) isrc->impl;

    assert(idx>=0 && idx < impl->nformats);
    return impl->formats[idx];
}

static int get_current_format(image_source_t *isrc)
{
    assert(isrc->impl_type == IMPL_TYPE);
    impl_dc1394_t *impl = (impl_dc1394_t*) isrc->impl;

    return impl->current_format_idx;
}

static int set_format(image_source_t *isrc, int idx)
{
    assert(isrc->impl_type == IMPL_TYPE);
    impl_dc1394_t *impl = (impl_dc1394_t*) isrc->impl;

    assert(idx>=0 && idx < impl->nformats);

    impl->current_format_idx = idx;

    return 0;
}

static int num_features(image_source_t *isrc)
{
    return 5;
}

static const char* get_feature_name(image_source_t *isrc, int idx)
{
    switch(idx)
    {
    case 0:
        return "WHITEBALANCE_RED";
    case 1:
        return "WHITEBALANCE_BLUE";
    case 2:
        return "EXPOSURE";
    case 3:
        return "BRIGHTNESS";
    case 4:
        return "GAMMA";
    case 5:
        return "FRAME_RATE";
    default:
        return NULL;
    }
}

static double get_feature_min(image_source_t *isrc, int idx)
{
    switch(idx)
    {
    case 0:
    case 1:
    case 2:
        return 0;
    case 3:
        return 1;
    case 4:
        return 0;
    case 5:
        return 9; // XXX Hack
    default:
        return 0;
    }
}

static double get_feature_max(image_source_t *isrc, int idx)
{
    switch(idx)
    {
    case 0:
    case 1:
        return 1023;
    case 2:
        return 62;
    case 3:
        return 255;
    case 4:
        return 1;
    case 5:
        return 61; // XXX Hack
    default:
        return 0;
    }
}

static double get_feature_value(image_source_t *isrc, int idx)
{
    uint32_t r, b;
    impl_dc1394_t *impl = (impl_dc1394_t*) isrc->impl;

    switch (idx)
    {
    case 0:
    case 1: {
        dc1394_feature_whitebalance_get_value(impl->cam, &b, &r);

        if (idx == 0)
            return r;

        return b;
    }

    case 2: {
        uint32_t v = 0;
        dc1394_feature_get_value(impl->cam, DC1394_FEATURE_EXPOSURE, &v); // XXX error checking
        return v;
    }

    case 3: {
        uint32_t v = 0;
        dc1394_feature_get_value(impl->cam, DC1394_FEATURE_BRIGHTNESS, &v); // XXX error checking
        return v;
    }

    case 4: {
        uint32_t v = 0;
        dc1394_feature_get_value(impl->cam, DC1394_FEATURE_GAMMA, &v); // XXX error checking
        return v;
    }

    case 5: {
        uint32_t v = 0;
        dc1394_feature_get_value(impl->cam, DC1394_FEATURE_FRAME_RATE, &v); // XXX error checking
        return v;
    }

    default:
        return 0;
    }
}

static int set_feature_value(image_source_t *isrc, int idx, double v)
{
    uint32_t r, b;
    impl_dc1394_t *impl = (impl_dc1394_t*) isrc->impl;

    dc1394_feature_whitebalance_get_value(impl->cam, &b, &r);

    switch (idx)
    {
    case 0:
    case 1: {
        if (idx==0)
            r = (uint32_t) v;
        if (idx==1)
            b = (uint32_t) v;

        return dc1394_feature_whitebalance_set_value(impl->cam, (uint32_t) b, (uint32_t) r);
    }

    case 2:
        return dc1394_feature_set_value(impl->cam, DC1394_FEATURE_EXPOSURE, (uint32_t) v);

    case 3:
        return dc1394_feature_set_value(impl->cam, DC1394_FEATURE_BRIGHTNESS, (uint32_t) v);

    case 4:
        return dc1394_feature_set_value(impl->cam, DC1394_FEATURE_GAMMA, (uint32_t) v);

    case 5:
        return dc1394_feature_set_value(impl->cam, DC1394_FEATURE_FRAME_RATE, (uint32_t) v);

    default:
        return 0;
    }
}

static int start(image_source_t *isrc)
{
    int have_reset_bus = 0;

restart:
    assert(isrc->impl_type == IMPL_TYPE);
    impl_dc1394_t *impl = (impl_dc1394_t*) isrc->impl;

    image_source_format_t *format = impl->formats[impl->current_format_idx];
    struct format_priv *format_priv = format->priv;

    dc1394_video_set_mode(impl->cam, format_priv->dc1394_mode);
    dc1394_video_set_iso_speed(impl->cam, DC1394_ISO_SPEED_400);

    assert(dc1394_is_video_mode_scalable(format_priv->dc1394_mode));

    dc1394format7modeset_t info;
    dc1394_format7_get_modeset(impl->cam, &info);

    dc1394format7mode_t *mode = info.mode + format_priv->format7_mode_idx;
    dc1394color_coding_t color_coding = mode->color_codings.codings[format_priv->color_coding_idx];

    dc1394_format7_set_image_size(impl->cam, format_priv->dc1394_mode,
                                  format->width, format->height);

    dc1394_format7_set_image_position(impl->cam, format_priv->dc1394_mode, 0, 0);

    dc1394_format7_set_color_coding(impl->cam, format_priv->dc1394_mode, color_coding);

    uint32_t psize_unit, psize_max;
    dc1394_format7_get_packet_parameters(impl->cam, format_priv->dc1394_mode, &psize_unit, &psize_max);
    int packet_size = psize_max; //4096;

    dc1394_format7_set_packet_size(impl->cam, format_priv->dc1394_mode, packet_size);
    uint64_t bytes_per_frame;
    dc1394_format7_get_total_bytes(impl->cam, format_priv->dc1394_mode, &bytes_per_frame);

    if (bytes_per_frame * impl->num_buffers > 25000000) {
        printf ("Reducing dc1394 buffers from %d to ", impl->num_buffers);
        impl->num_buffers = 25000000 / bytes_per_frame;
        printf ("%d\n", impl->num_buffers);
    }

    /* Using libdc1394 for iso streaming */
    if (dc1394_capture_setup(impl->cam, impl->num_buffers,
                             DC1394_CAPTURE_FLAGS_DEFAULT) != DC1394_SUCCESS)
        goto fail;

    if (dc1394_video_set_transmission(impl->cam, DC1394_ON) != DC1394_SUCCESS)
        goto fail;

    impl->fd = dc1394_capture_get_fileno (impl->cam);

    return 0;

fail:
    if (have_reset_bus) {
        fprintf(stderr, "----------------------------------------------------------------\n");
        fprintf(stderr, "Error: failed to initialize dc1394 stream\n");
        fprintf(stderr, "\nIF YOU HAVE HAD A CAMERA FAIL TO EXIT CLEANLY OR\n");
        fprintf(stderr, " THE BANDWIDTH HAS BEEN OVER SUBSCRIBED TRY (to reset):\n");
        fprintf(stderr, "dc1394_reset_bus\n\n");
        fprintf(stderr, "----------------------------------------------------------------\n");
        return -1;
    } else {
        fprintf(stderr, "----------------------------------------------------------------\n");
        fprintf(stderr, "image_source_dc1394: Camera startup failed, reseting bus.\n");
        fprintf(stderr, "(this is harmless if the last program didn't quit cleanly,\n");
        fprintf(stderr, "but things may not work well if bandwidth is over-subscribed.\n");
        fprintf(stderr, "----------------------------------------------------------------\n");

        dc1394_reset_bus(impl->cam);

        have_reset_bus = 1;
        goto restart;
    }
}

static int get_frame(image_source_t *isrc, void **imbuf, int *buflen)
{
    assert(isrc->impl_type == IMPL_TYPE);
    impl_dc1394_t *impl = (impl_dc1394_t*) isrc->impl;

    assert(impl->current_frame == NULL);

    while (1) {

        if (dc1394_capture_dequeue(impl->cam, DC1394_CAPTURE_POLICY_WAIT, &impl->current_frame) != DC1394_SUCCESS) {
            printf("DC1394 dequeue failed\n");
            return -1;
        }

        if (impl->current_frame->frames_behind > 0 || dc1394_capture_is_frame_corrupt(impl->cam, impl->current_frame) == DC1394_TRUE) {
            dc1394_capture_enqueue(impl->cam, impl->current_frame);
            continue;
        }

        break;
    }

    *imbuf = impl->current_frame->image;
    *buflen = impl->current_frame->image_bytes;

    return 0;
}

static int release_frame(image_source_t *isrc, void *imbuf)
{
    assert(isrc->impl_type == IMPL_TYPE);
    impl_dc1394_t *impl = (impl_dc1394_t*) isrc->impl;

    dc1394_capture_enqueue(impl->cam, impl->current_frame);
    impl->current_frame = NULL;

    return 0;
}

static int stop(image_source_t *isrc)
{
    assert(isrc->impl_type == IMPL_TYPE);
    impl_dc1394_t *impl = (impl_dc1394_t*) isrc->impl;

    dc1394_video_set_transmission (impl->cam, DC1394_OFF);

    dc1394_capture_stop(impl->cam);


    return 0;
}

static int my_close(image_source_t *isrc)
{
    assert(isrc->impl_type == IMPL_TYPE);
    impl_dc1394_t *impl = (impl_dc1394_t*) isrc->impl;

    return close(impl->fd);
}

/** Open the given guid, or if -1, open the first camera available. **/
image_source_t *image_source_dc1394_open(int64_t guid)
{
    image_source_t *isrc = calloc(1, sizeof(image_source_t));
    impl_dc1394_t *impl = calloc(1, sizeof(impl_dc1394_t));

    isrc->impl_type = IMPL_TYPE;
    isrc->impl = impl;

    isrc->num_formats = num_formats;
    isrc->get_format = get_format;
    isrc->get_current_format = get_current_format;
    isrc->set_format = set_format;
    isrc->num_features = num_features;
    isrc->get_feature_name = get_feature_name;
    isrc->get_feature_min = get_feature_min;
    isrc->get_feature_max = get_feature_max;
    isrc->get_feature_value = get_feature_value;
    isrc->set_feature_value = set_feature_value;
    isrc->start = start;
    isrc->get_frame = get_frame;
    isrc->release_frame = release_frame;
    isrc->stop = stop;
    isrc->close = my_close;

    impl->num_buffers = 10;

    impl->dc1394 = dc1394_new();
    if (!impl->dc1394)
        return NULL;

    // now open our desired camera.
    impl->cam = dc1394_camera_new(impl->dc1394, guid);
    if (impl->cam == NULL)
        goto fail;

    dc1394format7modeset_t info;
    if (dc1394_format7_get_modeset(impl->cam, &info) != DC1394_SUCCESS)
        goto fail;

    for (int i = 0; i < DC1394_VIDEO_MODE_FORMAT7_NUM; i++) {

        dc1394format7mode_t *mode = info.mode + i;

        if (!info.mode[i].present)
            continue;

        for (int j = 0; j < mode->color_codings.num; j++) {

            impl->formats = realloc(impl->formats, (impl->nformats+1) * sizeof(image_source_format_t*));
            impl->formats[impl->nformats] = calloc(1, sizeof(image_source_format_t));

            impl->formats[impl->nformats]->width = mode->max_size_x;
            impl->formats[impl->nformats]->height = mode->max_size_y;
            impl->formats[impl->nformats]->format = strdup(toformat(mode->color_codings.codings[j], mode->color_filter));

            struct format_priv *format_priv = calloc(1, sizeof(struct format_priv));
            impl->formats[impl->nformats]->priv = format_priv;

            format_priv->dc1394_mode = DC1394_VIDEO_MODE_FORMAT7_0 + i;
            format_priv->format7_mode_idx = i;
            format_priv->color_coding_idx = j;

            impl->nformats++;
        }
    }

    if (1) {
        // work around an intermittent bug where sometimes some
        // garbage causes the camera data to be offset by about a
        // third of a scanline.
        isrc->start(isrc);

        void *imbuf = NULL;
        int imbuflen = 0;

        if (!isrc->get_frame(isrc, &imbuf, &imbuflen)) {
            isrc->release_frame(isrc, imbuf);
        }

        isrc->stop(isrc);
    }

    return isrc;

fail:
    printf("image_source_dc1394_open: failure\n");
    return NULL;
}

char** image_source_enumerate_dc1394(char **urls)
{
    dc1394_t *dc1394;
    dc1394camera_list_t *list;

    dc1394 = dc1394_new();
    if (dc1394 == NULL)
        return urls;

    if (dc1394_camera_enumerate (dc1394, &list) < 0)
        goto exit;

    // display all cameras for convenience
    for (int i = 0; i < list->num; i++) {
        dc1394camera_t *cam = dc1394_camera_new(dc1394, list->ids[i].guid);
        char buf[1024];

        // other useful fields: cam->vendor, cam->model);
        snprintf(buf, 1024, "dc1394://%"PRIx64, list->ids[i].guid);
        urls = string_array_add(urls, buf);
        dc1394_camera_free(cam);
    }

    dc1394_camera_free_list(list);

exit:
    dc1394_free(dc1394);
    return urls;
}
