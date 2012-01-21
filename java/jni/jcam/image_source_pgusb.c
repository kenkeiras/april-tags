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
#include <sys/types.h>
#include <libusb-1.0/libusb.h>
#include <pthread.h>

/*
http://www.1394ta.org/press/WhitePapers/Firewire%20Reference%20Tutorial.pdf
http://damien.douxchamps.net/ieee1394/libdc1394/iidc/IIDC_1.31.pdf
*/

#define IMAGE_SOURCE_UTILS
#include "image_source.h"

#define IMPL_TYPE 0x7123b65a

struct transfer_info
{
    struct libusb_transfer *transfer;
    uint8_t *buf;
};

typedef struct impl_pgusb impl_pgusb_t;
struct impl_pgusb
{
    libusb_context *context;
    libusb_device *dev;
    libusb_device_handle *handle;

    int nformats;
    image_source_format_t **formats;
    int                   current_format_idx;

    // must add CONFIG_ROM_BASE to each of these.
    uint32_t unit_directory_offset;
    uint32_t unit_dependent_directory_offset;
    uint32_t command_regs_base;

//    int nframes;
//    struct usb_frame *frames;

    int bytes_per_frame; // how many bytes are actually used in each image?
    int transfer_size;

    int packet_size;
    int packets_per_image;

    int nrecords;
    struct transfer_info *records;

    int current_user_frame; // what frame is currently in the user's possession?
    pthread_t worker_thread;
    volatile int started;

    pthread_mutex_t queue_mutex;
    pthread_cond_t queue_cond;
    int *queue; // indices of frames that are available.
    volatile int queue_avail; // how many frames are available?
};

struct format_priv
{
    int format7_mode_idx;
    int color_coding_idx;
    uint32_t csr;
};

struct usb_vendor_product
{
    uint16_t vendor;
    uint16_t product;
};

static struct usb_vendor_product vendor_products[] =
{
    { 0x1e10, 0x2000 }, // Point Grey Firefly MV Color
    { 0x1e10, 0x2001 }, // Point Grey Firefly MV Mono
    { 0x1e10, 0x2004 }, // Point Grey Chameleon Color
    { 0x1e10, 0x2005 }, // Point Grey Chameleon Mono
    { 0, 0 },
};

static const char* COLOR_MODES[] = { "GRAY", "YUV422", "UYVY", "IYU2", "RGB",
                                     "GRAY16", "RGB16", "GRAY16", "SRGB16", "RAW8", "RAW16" };
static const char* BAYER_MODES[] = { "BAYER_RGGB", "BAYER_GBRG", "BAYER_GRBG", "BAYER_BGGR" };

#define REQUEST_TIMEOUT_MS 1000
#define CONFIG_ROM_BASE             0xFFFFF0000000ULL

// convert a base-16 number in ASCII ('len' characters long) to a 64
// bit integer. Result is written to *ov, 0 is returned if parsing is
// successful. Otherwise -1 is returned.
static int strto64(const char *s, int maxlen, int64_t *ov)
{
    int64_t acc = 0;
    for (int i = 0; i < maxlen; i++) {
        char c = s[i];
        if (c==0)
            break;
        int ic = 0;
        if (c >= 'a' && c <='f')
            ic = c - 'a' + 10;
        else if (c >= 'A' && c <= 'F')
            ic = c - 'A' + 10;
        else if (c >= '0' && c <= '9')
            ic = c - '0';
        else
            printf("%c", c); //return -1;
        acc = (acc<<4) + ic;
    }

    *ov = acc;
    return 0;
}

static int strposat(const char *haystack, const char *needle, int haystackpos)
{
    int idx = haystackpos;
    int needlelen = strlen(needle);

    while (haystack[idx] != 0) {
        if (!strncmp(&haystack[idx], needle, needlelen))
            return idx;

        idx++;
    }

    return -1; // not found.
}

static int strpos(const char *haystack, const char *needle)
{
    return strposat(haystack, needle, 0);
}

// The high 16 bits of the IEEE 1394 address space are mapped to the
// request byte of USB control transfers.  Only a discrete set
// addresses are currently supported, as mapped by this function.
static int address_to_request(uint64_t address)
{
    switch (address >> 32) {
        case 0xffff:
            return 0x7f;
        case 0xd000:
            return 0x80;
        case 0xd0001:
            return 0x81;
    }

    assert(0);
    return -1;
}

static void put_ready_frame(impl_pgusb_t *impl, int idx)
{
    pthread_mutex_lock(&impl->queue_mutex);

    assert(impl->queue_avail < impl->nrecords);

    impl->queue[impl->queue_avail] = idx;
    impl->queue_avail++;

    pthread_cond_broadcast(&impl->queue_cond);
    pthread_mutex_unlock(&impl->queue_mutex);
}

static int get_ready_frame(impl_pgusb_t *impl)
{
    pthread_mutex_lock(&impl->queue_mutex);

    int idx = -1;

    while (impl->queue_avail == 0) {
        pthread_cond_wait(&impl->queue_cond, &impl->queue_mutex);
    }

    idx = impl->queue[0];
    impl->queue_avail--;

    for (int i = 0; i < impl->queue_avail; i++)
        impl->queue[i] = impl->queue[i+1];

    pthread_mutex_unlock(&impl->queue_mutex);

    return idx;
}

static int do_read(libusb_device_handle *handle, uint64_t address, uint32_t *quads, int num_quads)
{
    int request = address_to_request(address);
    if (request < 0)
        return -1;

    unsigned char buf[num_quads*4];

    // IEEE 1394 address reads are mapped to USB control transfers as
    // shown here.
    int ret = libusb_control_transfer (handle, 0xc0, request,
                                       address & 0xffff, (address >> 16) & 0xffff,
                                       buf, num_quads * 4, REQUEST_TIMEOUT_MS);
    if (ret < 0)
        return -1;

    int ret_quads = (ret + 3) / 4;

    // Convert from little-endian to host-endian
    for (int i = 0; i < ret_quads; i++) {
        quads[i] = (buf[4*i+3] << 24) | (buf[4*i+2] << 16)
            | (buf[4*i+1] << 8) | buf[4*i];
    }

    return ret_quads;
}

static int do_write(libusb_device_handle *handle, uint64_t address, uint32_t *quads, int num_quads)
{
    int request = address_to_request(address);
    if (request < 0)
        return -1;

    unsigned char buf[num_quads*4];

    // Convert from host-endian to little-endian
    for (int i = 0; i < num_quads; i++) {
        buf[4*i]   = quads[0] & 0xff;
        buf[4*i+1] = (quads[0] >> 8) & 0xff;
        buf[4*i+2] = (quads[0] >> 16) & 0xff;
        buf[4*i+3] = (quads[0] >> 24) & 0xff;
    }

    // IEEE 1394 address writes are mapped to USB control transfers as
    // shown here.
    int ret = libusb_control_transfer (handle, 0x40, request,
                                       address & 0xffff, (address >> 16) & 0xffff,
                                       buf, num_quads * 4, REQUEST_TIMEOUT_MS);
    if (ret < 0)
        return -1;
    return ret / 4;
}

// returns number of quads actually read.
static int read_config_rom(libusb_device *dev, uint32_t *quads, int nquads)
{
    libusb_device_handle *handle;
    if (libusb_open(dev, &handle) < 0) {
        printf("error");
        return -1;
    }

    int i = 0;
    for (i = 0; i < nquads; i++) {
        int ret = do_read(handle, CONFIG_ROM_BASE + 0x400 + 4*i, &quads[i], 1);
        if (ret < 1)
            break;
    }

    libusb_close(handle);
    return i;
}

static uint64_t get_guid(libusb_device *dev)
{
    uint32_t config[256];

    if (read_config_rom(dev, config, 256) < 0) {
        printf("error reading camera GUID\n");
        return -1;
    }

    // not an IIDC camera?
    if ((config[0] >> 24) != 0x4)
        return 0;

    return ((uint64_t) config[3] << 32) | config[4];
}

char** image_source_enumerate_pgusb(char **urls)
{
    libusb_context *context;

    if (libusb_init(&context) != 0) {
        printf("Couldn't initialize libusb\n");
        return NULL;
    }

    libusb_device **devs;
    int ndevs = libusb_get_device_list(context, &devs);

    for (int i = 0; i < ndevs; i++) {
        struct libusb_device_descriptor desc;
        if (libusb_get_device_descriptor(devs[i], &desc) != 0) {
            printf("couldn't get descriptor for device %d\n", i);
            continue;
        }

        int okay = 0;
        for (int j = 0; vendor_products[j].vendor != 0; j++) {
            if (desc.idVendor == vendor_products[j].vendor &&
                desc.idProduct == vendor_products[j].product) {
                okay = 1;
            }
        }

        if (okay) {
            uint64_t guid = get_guid(devs[i]);

            char buf[1024];
            snprintf(buf, 1024, "pgusb://%"PRIx64, guid);

            urls = string_array_add(urls, buf);
        }
    }

    libusb_free_device_list(devs, 1);

    libusb_exit(context);

    return urls;
}


static int num_formats(image_source_t *isrc)
{
    assert(isrc->impl_type == IMPL_TYPE);
    impl_pgusb_t *impl = (impl_pgusb_t*) isrc->impl;

    return impl->nformats;
}

static image_source_format_t *get_format(image_source_t *isrc, int idx)
{
    assert(isrc->impl_type == IMPL_TYPE);
    impl_pgusb_t *impl = (impl_pgusb_t*) isrc->impl;

    assert(idx>=0 && idx < impl->nformats);
    return impl->formats[idx];
}

static int get_current_format(image_source_t *isrc)
{
    assert(isrc->impl_type == IMPL_TYPE);
    impl_pgusb_t *impl = (impl_pgusb_t*) isrc->impl;

    return impl->current_format_idx;
}

static int set_format(image_source_t *isrc, int idx)
{
    assert(isrc->impl_type == IMPL_TYPE);
    impl_pgusb_t *impl = (impl_pgusb_t*) isrc->impl;

    assert(idx>=0 && idx < impl->nformats);

    impl->current_format_idx = idx;

    return 0;
}

static int set_named_format(image_source_t *isrc, const char *desired_format)
{
    assert(isrc->impl_type == IMPL_TYPE);
    impl_pgusb_t *impl = (impl_pgusb_t*) isrc->impl;

    const char *format_name = desired_format;
    int colonpos = strpos(desired_format, ":");
    int xpos = strpos(desired_format, "x");
    int width = -1;
    int height = -1;
    if (colonpos >= 0 && xpos > colonpos) {
        format_name = strndup(desired_format, colonpos);
        char *swidth = strndup(&desired_format[colonpos+1], xpos-colonpos-1);
        char *sheight = strdup(&desired_format[xpos+1]);

        width = atoi(swidth);
        height = atoi(sheight);

        free(swidth);
        free(sheight);
    }

    int nformats = num_formats(isrc);
    int fidx = -1;

    for (int i=0; i < nformats; i++)
    {
        image_source_format_t *fmt = get_format(isrc, i);

        if (!strcmp(fmt->format, format_name)) {
            if (width == -1 || height == -1 || (fmt->width == width && fmt->height == height)) {
                fidx = i;
                break;
            }
        }
    }

    // if no matching format found...
    if (fidx < 0 || fidx >= impl->nformats) {
        printf("Matching format '%s' not found. Valid formats are:\n", desired_format);
        for (int i=0; i < nformats; i++)
        {
            image_source_format_t *fmt = get_format(isrc, i);
            printf("\t[fidx: %d] width: %d height: %d name: '%s'\n",
                   i, fmt->width, fmt->height, fmt->format);
        }
        printf("\tFormat resolution not required.  Exiting.\n");
        exit(-1);
    }

    impl->current_format_idx = fidx;

    return 0;
}

static int num_features(image_source_t *isrc)
{
    printf("num_features\n");

    // don't forget: feature index starts at 0
    return 0;
}

static void callback(struct libusb_transfer *transfer)
{
    image_source_t *isrc = (image_source_t*) transfer->user_data;
    assert(isrc->impl_type == IMPL_TYPE);
    impl_pgusb_t *impl = (impl_pgusb_t*) isrc->impl;
    image_source_format_t *ifmt = get_format(isrc, get_current_format(isrc));

    // which record is it?
    int idx = -1;
    for (int i = 0; i < impl->nrecords; i++) {
        if (impl->records[i].transfer == transfer) {
            idx = i;
            break;
        }
    }
    if (idx < 0 || idx >= impl->nrecords) {
        printf("BAD IDX %d %p\n", idx, transfer);
        return; //assert(0);
    }

    if (transfer->status == LIBUSB_TRANSFER_COMPLETED && transfer->actual_length == transfer->length) {

        put_ready_frame(impl, idx);

    } else {
        // transfer failed. Just queue that buffer up again.
        if (transfer->status == LIBUSB_TRANSFER_OVERFLOW) {
            // device sent too much (?)
            printf("%s:%d usb transfer failed; device sent %d, requested %d\n",
                   __FILE__, __LINE__, transfer->actual_length, transfer->length);
        } else {
            printf("%s:%d usb transfer status %d\n",
                   __FILE__, __LINE__, transfer->status);
        }

        libusb_fill_bulk_transfer(impl->records[idx].transfer, impl->handle,
                                  0x81, impl->records[idx].buf, impl->transfer_size, callback, isrc, 0);

        if (libusb_submit_transfer(impl->records[idx].transfer) < 0) {
            printf("submit failed\n");
        }
    }
}

static void *worker_thread_proc(void *arg)
{
    image_source_t *isrc = (image_source_t*) arg;
    impl_pgusb_t *impl = (impl_pgusb_t*) isrc->impl;

    while (impl->started) {

        struct timeval tv = {
            .tv_sec = 0,
            .tv_usec = 100000,
        };

        libusb_handle_events_timeout(impl->context, &tv);
    }

    return NULL;
}

static int start(image_source_t *isrc)
{
    assert(isrc->impl_type == IMPL_TYPE);
    impl_pgusb_t *impl = (impl_pgusb_t*) isrc->impl;
    image_source_format_t *ifmt = get_format(isrc, get_current_format(isrc));
    struct format_priv *format_priv = (struct format_priv*) ifmt->priv;

/*
    for (int i = 0x0600; i <= 0x0630; i+=4) {
        uint32_t d;
        do_read(impl->handle, CONFIG_ROM_BASE + impl->command_regs_base + i, &d, 1);
        printf("%04x: %08x\n", i, d);
    }
*/

    // set iso channel
    if (1) {
        uint32_t quads[] = { 0x02000000 }; //
        if (do_write(impl->handle, CONFIG_ROM_BASE + impl->command_regs_base + 0x60c, quads, 1) != 1)
            printf("failed write: line %d\n", __LINE__);
    }

    // shutter 81c = c3000212


    // gain 820 = c3000040


    // feature_hi? 834 = c0000000

    // feature_hi? 83c = c30001e0

    // set format 7  (608 = e0000000)
    if (1) {
        uint32_t quads[] = { 0xe0000000 }; // 7 << 29
        if (do_write(impl->handle, CONFIG_ROM_BASE + impl->command_regs_base + 0x608, quads, 1) != 1)
            printf("failed write: line %d\n", __LINE__);
    }

    // set format 7 mode (604 = 00000000)
    if (1) {
        uint32_t quads[] = { format_priv->format7_mode_idx<<24 };
        if (do_write(impl->handle, CONFIG_ROM_BASE + impl->command_regs_base + 0x604, quads, 1) != 1)
            printf("failed write: line %d\n", __LINE__);
    }

    // set iso channel (again?) 60c = 02000000 (UNNECESSARY?)

    // set image size a0c = 02f001e0
    if (1) {
        uint32_t quads[] = { ifmt->height + (ifmt->width<<16) };
        if (do_write(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x0c, quads, 1) != 1)
            printf("failed write: line %d\n", __LINE__);
    }

    // set image position a08 = 00000000
    if (1) {
        uint32_t quads[] = { 0 };
        if (do_write(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x08, quads, 1) != 1)
            printf("failed write: line %d\n", __LINE__);
    }

    // set format 7 color mode (a10 = 00000000)
    if (1) {
        uint32_t quads[] = { format_priv->color_coding_idx<<24 };
        if (do_write(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x10, quads, 1) != 1)
            printf("failed write: line %d\n", __LINE__);
    }

    // a7c = 40000000
    // perform VALUE_SETTING; request updated packet parameters and wait for task to complete.
    if (1) {

        uint32_t quads[] = { 0x40000000 };
        if (do_write(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x7c, quads, 1) != 1)
            printf("failed write: line %d\n", __LINE__);

        while (1) {
            uint32_t resp;

            do_read(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x7c, &resp, 1);

            if (resp & 0x40000000)
                continue;
            break;
        }
    }

    if (1) {
        uint32_t pixels_per_frame;
        do_read(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x34, &pixels_per_frame, 1);

        uint32_t total_bytes_hi, total_bytes_lo;
        do_read(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x38, &total_bytes_hi, 1);
        do_read(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x3c, &total_bytes_lo, 1);

        printf("pixels_per_frame %08x, total_bytes_hi %08x, total_bytes_lo %08x\n",
               pixels_per_frame, total_bytes_hi, total_bytes_lo);
    }

    // a44 = 0bc00000
    if (1) {
        uint32_t packet_size;
        uint32_t packet_param;

        do_read(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x40, &packet_param, 1);

        // set packet size
        do_read(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x44, &packet_size, 1);
        printf("packet param %08x, bytes per packet %08x\n", packet_param, packet_size);

//        packet_size = packet_param & 0xffff; // max packet size
        uint32_t quads[] = { (packet_size&0xffff)<<16 | (packet_size&0xffff)};

        if (do_write(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x44, quads, 1) != 1)
            printf("failed write: line %d\n", __LINE__);

        impl->packet_size = packet_size & 0xffff;
    }

    // 614 = 80000000 (streaming = on)


    // set up USB transfers
    if (libusb_claim_interface(impl->handle, 0) < 0) {
        printf("couldn't claim interface\n");
    }

    if (0) {
        for (int i = 0x0600; i <= 0x0630; i+=4) {
            uint32_t d;
            do_read(impl->handle, CONFIG_ROM_BASE + impl->command_regs_base + i, &d, 1);
            printf("%04x: %08x\n", i, d);
        }
    }

    if (0) {
        int nquads = 32;
        uint32_t quads[32];
        if (do_read(impl->handle, CONFIG_ROM_BASE + format_priv->csr, quads, nquads) != nquads)
            return -1;

        for (int i = 0; i < 32; i++)
            printf(" %03x : %08x\n", i*4, quads[i]);
    }

    impl->nrecords = 4;
    impl->records = (struct transfer_info*) calloc(impl->nrecords, sizeof(struct transfer_info));
    impl->queue = (int*) calloc(impl->nrecords, sizeof(int));

    int bytes_per_pixel = 1;
    if ((format_priv->color_coding_idx >=5 && format_priv->color_coding_idx <= 8) ||
        (format_priv->color_coding_idx == 10)) {
        bytes_per_pixel = 2;
    }


    impl->bytes_per_frame = ifmt->width * ifmt->height * bytes_per_pixel;

    uint32_t packets_per_image = (impl->bytes_per_frame + impl->packet_size - 1) / impl->packet_size;

    impl->transfer_size = packets_per_image * impl->packet_size;

    printf("bytes_per_frame %d, transfer_size %d\n", impl->bytes_per_frame, impl->transfer_size);

    for (int i = 0; i < impl->nrecords; i++) {
        impl->records[i].transfer = libusb_alloc_transfer(0);
        impl->records[i].buf = malloc(impl->transfer_size);
        libusb_fill_bulk_transfer(impl->records[i].transfer, impl->handle,
                                  0x81, impl->records[i].buf, impl->transfer_size, callback, isrc, 0);

        impl->records[i].transfer->flags = LIBUSB_TRANSFER_SHORT_NOT_OK;

        if (libusb_submit_transfer(impl->records[i].transfer) < 0) {
            printf("submit failed\n");
        }
    }

    // set transmission to ON
    if (1) {
        uint32_t quads[] = { 0x80000000UL };
        if (do_write(impl->handle, CONFIG_ROM_BASE + impl->command_regs_base + 0x614, quads, 1) != 1)
            printf("ack!\n");
    }

    impl->started = 1;

    if (pthread_create(&impl->worker_thread, NULL, worker_thread_proc, isrc) != 0) {
        perror("pthread");
        return -1;
    }

    return 0;
}

static int get_frame(image_source_t *isrc, void **imbuf, int *buflen)
{
    assert(isrc->impl_type == IMPL_TYPE);
    impl_pgusb_t *impl = (impl_pgusb_t*) isrc->impl;
    image_source_format_t *ifmt = get_format(isrc, get_current_format(isrc));

    int idx = get_ready_frame(impl);
    impl->current_user_frame = idx;

    *buflen = impl->bytes_per_frame;
    *imbuf = impl->records[idx].buf;

    return 0;
}

static int release_frame(image_source_t *isrc, void *imbuf)
{
    assert(isrc->impl_type == IMPL_TYPE);
    impl_pgusb_t *impl = (impl_pgusb_t*) isrc->impl;

    assert(impl->current_user_frame >= 0);

    assert(impl->records[impl->current_user_frame].buf == imbuf);

    libusb_fill_bulk_transfer(impl->records[impl->current_user_frame].transfer, impl->handle,
                              0x81, impl->records[impl->current_user_frame].buf,
                              impl->transfer_size, callback, isrc, 0);

    if (libusb_submit_transfer(impl->records[impl->current_user_frame].transfer) < 0) {
        printf("submit failed\n");
    }

    impl->current_user_frame = -1;

    return 0;
}

static int stop(image_source_t *isrc)
{
    assert(isrc->impl_type == IMPL_TYPE);
    impl_pgusb_t *impl = (impl_pgusb_t*) isrc->impl;

    for (int i = 0; i < impl->nrecords; i++) {
        printf("CONSUME %d\n", i);
        get_ready_frame(impl);
    }

    impl->started = 0;
    pthread_join(impl->worker_thread, NULL);

    // set transmission to OFF
    if (1) {
        uint32_t quads[] = { 0x00000000UL };
        if (do_write(impl->handle, CONFIG_ROM_BASE + impl->command_regs_base + 0x614, quads, 1) != 1)
            printf("ack!\n");
    }

    for (int i = 0; i < impl->nrecords; i++) {
        libusb_free_transfer(impl->records[i].transfer);
        free(impl->records[i].buf);
    }
    free(impl->records);
    free(impl->queue);


    libusb_release_interface(impl->handle, 0);



//    for (int i = 0; i < impl->nrecords; i++)
//        libusb_cancel_transfer(impl->records[i].transfer);

    return 0;
}

static int my_close(image_source_t *isrc)
{
    assert(isrc->impl_type == IMPL_TYPE);
    impl_pgusb_t *impl = (impl_pgusb_t*) isrc->impl;

//    for (int i = 0; i < impl->nrecords; i++)
//        libusb_cancel_transfer(impl->records[i].transfer);

    printf("XXX UNIMPLEMENTED CLOSE\n");

    libusb_close(impl->handle);
    libusb_exit(impl->context);

    return 0;
}

image_source_t *image_source_pgusb_open(url_parser_t *urlp)
{
//    const char *protocol = url_parser_get_protocol(urlp);
    const char *location = url_parser_get_location(urlp);

    libusb_context *context;
    if (libusb_init(&context) != 0) {
        printf("Couldn't initialize libusb\n");
        return NULL;
    }

    libusb_device **devs;
    int ndevs = libusb_get_device_list(context, &devs);

    int64_t guid = 0;
    if (strlen(location) > 0) {
        if (strto64(location, strlen(location), &guid)) {
            printf("image_source_open: pgusb guid '%s' is not a valid integer.\n", location);
            return NULL;
        }
    }

    // Look for a device whose guid matches what the user specified
    // (or, if the user didn't specify a guid, just pick the first
    // camera.
    libusb_device *dev = NULL;
    for (int i = 0; i < ndevs; i++) {
        struct libusb_device_descriptor desc;
        if (libusb_get_device_descriptor(devs[i], &desc) != 0) {
            printf("couldn't get descriptor for device %d\n", i);
            continue;
        }

        int okay = 0;
        for (int j = 0; vendor_products[j].vendor != 0; j++) {
            if (desc.idVendor == vendor_products[j].vendor &&
                desc.idProduct == vendor_products[j].product) {
                okay = 1;
            }
        }

        if (okay) {
            uint64_t this_guid = get_guid(devs[i]);

            if (guid == 0 || guid == this_guid) {
                dev = devs[i];
                break;
            }
        }
    }

    if (dev == NULL)
        return NULL;

    image_source_t *isrc = calloc(1, sizeof(image_source_t));
    impl_pgusb_t *impl = calloc(1, sizeof(impl_pgusb_t));

    isrc->impl_type = IMPL_TYPE;
    isrc->impl = impl;

    impl->context = context;
    impl->dev = dev;

    pthread_cond_init(&impl->queue_cond, NULL);
    pthread_mutex_init(&impl->queue_mutex, NULL);

    if (libusb_open(impl->dev, &impl->handle) < 0) {
        printf("error\n");
        return NULL;
    }

    if (libusb_set_configuration(impl->handle, 1) < 0) {
        printf("error\n");
        return NULL;
    }

    uint32_t magic;
    if (do_read(impl->handle, CONFIG_ROM_BASE + 0x404, &magic, 1) != 1)
        return NULL;
    if (magic != 0x31333934)
        return NULL;

    if (1) {
        uint32_t tmp;
        if (do_read(impl->handle, CONFIG_ROM_BASE + 0x424, &tmp, 1) != 1)
            return NULL;

        assert((tmp>>24)==0xd1);
        impl->unit_directory_offset = 0x424 + (tmp & 0x00ffffff)*4;

        printf("unit_directory_offset: %08x\n", impl->unit_directory_offset);
    }

    if (1) {
        uint32_t tmp;
        if (do_read(impl->handle, CONFIG_ROM_BASE + impl->unit_directory_offset + 0x0c, &tmp, 1) != 1)
            return NULL;

        assert((tmp>>24)==0xd4);
        impl->unit_dependent_directory_offset = impl->unit_directory_offset + 0x0c + (tmp & 0x00ffffff)*4;

        printf("unit_dependent_directory_offset: %08x\n", impl->unit_dependent_directory_offset);
    }

    if (1) {
        uint32_t tmp;
        if (do_read(impl->handle, CONFIG_ROM_BASE + impl->unit_dependent_directory_offset + 0x4, &tmp, 1) != 1)
            return NULL;

        assert((tmp>>24)==0x40);
        impl->command_regs_base = 4*(tmp&0x00ffffff);

        printf("command_regs_base: %08x\n", impl->command_regs_base);
    }

/*
    for (int i = 0x0400; i <= 0x0500; i+=4) {
        uint32_t d;
        do_read(impl->handle, CONFIG_ROM_BASE + i, &d, 1);
        printf("%04x: %08x\n", i, d);
    }
*/

    if (1) {
        // which modes are supported by format 7?

        uint32_t v_mode_inq_7;
        if (do_read(impl->handle, CONFIG_ROM_BASE + impl->command_regs_base + 0x019c, &v_mode_inq_7, 1) != 1)
            return NULL;

        printf("v_mode_inq_7: %08x\n", v_mode_inq_7);

        v_mode_inq_7 = (v_mode_inq_7)>>24;

        for (int mode = 0; mode < 8; mode++) {
            if ((v_mode_inq_7 & (1<<(7-mode)))) {

                uint32_t mode_csr;
                if (do_read(impl->handle, CONFIG_ROM_BASE + impl->command_regs_base + 0x2e0 + mode*4, &mode_csr, 1) != 1)
                    return NULL;

                mode_csr *= 4;

                printf("mode %d csr %08x\n", mode, mode_csr);

                int nquads = 32;
                uint32_t quads[32];
                if (do_read(impl->handle, CONFIG_ROM_BASE + mode_csr, quads, nquads) != nquads)
                    return NULL;

/*
                for (int i = 0; i < 32; i++)
                    printf(" %03x : %08x\n", i*4, quads[i]);
*/

                uint32_t cmodes = quads[5];
                for (int cmode = 0; cmode < 11; cmode++) {
                    if (cmodes & (1<<(31-cmode))) {
                        printf(" %d %s\n", cmode, COLOR_MODES[cmode]);

                        impl->formats = realloc(impl->formats, (impl->nformats+1) * sizeof(image_source_format_t*));
                        impl->formats[impl->nformats] = calloc(1, sizeof(image_source_format_t));
                        impl->formats[impl->nformats]->height = quads[0] & 0xffff;
                        impl->formats[impl->nformats]->width = quads[0] >> 16;

                        if (cmode == 9 || cmode == 10) {
                            int filter_mode = quads[22]>>24;
                            impl->formats[impl->nformats]->format = strdup(BAYER_MODES[filter_mode]);
                        } else {
                            impl->formats[impl->nformats]->format = strdup(COLOR_MODES[cmode]);
                        }

                        struct format_priv *format_priv = calloc(1, sizeof(struct format_priv));
                        format_priv->format7_mode_idx = mode;
                        format_priv->color_coding_idx = cmode;
                        format_priv->csr = mode_csr;

                        impl->formats[impl->nformats]->priv = format_priv;
                        impl->nformats++;
                    }
                }
            }
        }
    }

    isrc->num_formats = num_formats;
    isrc->get_format = get_format;
    isrc->get_current_format = get_current_format;
    isrc->set_format = set_format;
    isrc->set_named_format = set_named_format;

    isrc->num_features = num_features;
/*    isrc->get_feature_name = get_feature_name;
    isrc->get_feature_min = get_feature_min;
    isrc->get_feature_max = get_feature_max;
    isrc->get_feature_value = get_feature_value;
    isrc->set_feature_value = set_feature_value;
*/
    isrc->start = start;
    isrc->get_frame = get_frame;
    isrc->release_frame = release_frame;

    isrc->stop = stop;
    isrc->close = my_close;

    printf("isrc %p\n", isrc);

    return isrc;
}

