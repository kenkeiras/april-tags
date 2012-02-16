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
#include <assert.h>

/*
http://www.1394ta.org/press/WhitePapers/Firewire%20Reference%20Tutorial.pdf
http://damien.douxchamps.net/ieee1394/libdc1394/iidc/IIDC_1.31.pdf


libusb thread safety must be carefully managed. Our strategy: we use
synchronous calls for configuration/etc, and asynchronous calls for
streaming data. To support asynchronous calls, we spawn off an event
handling thread. Because this event-handling thread uses the same
poll/selectx mechanisms as the libusb synchronous calls, we must
ensure that we are never performing synchronous calls while the thread
is running.

*/

// we need TIMEOUT_MS > 0 so that pending transfers eventually time
// out. Otherwise, stop() will never return.

// XXX Need a better way to stop streaming
#define REQUEST_TIMEOUT_MS 0
#define TIMEOUT_MS 0

#define IMAGE_SOURCE_UTILS
#include "image_source.h"

#define IMPL_TYPE 0x7123b65a

static int debug = 0;

struct image_info
{
    int status;

/** if 0, image is empty and available.
    if -1, image is being filled.
    if -2, image has been returned to user.
    if >=1, the frame is queued to be returned to the user, low numbers first.
**/

    uint8_t *buf;
};

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

    int bytes_per_frame; // how many bytes are actually used in each image?
    int bytes_transferred_per_frame; // how many bytes transferred per frame?
    int transfer_size;   // size of a single USB transaction

    int packet_size;
    int packets_per_image;

    int ntransfers;
    struct transfer_info *transfers;

    int nimages;
    struct image_info *images;

    pthread_t worker_thread;

    volatile int worker_exit_flag;

    // queue is used to access images (not transfers)
    pthread_mutex_t queue_mutex;
    pthread_cond_t queue_cond;

    int current_frame_index; // which frame are we receiving from the camera?
    int current_frame_offset;

    int current_user_frame; // what frame is currently in the user's possession?

    pthread_mutex_t pending_transaction_mutex;
    volatile int transfers_submitted;

    volatile int consume_transfers_flag;
};

struct format_priv
{
    uint32_t format7_mode_idx;
    uint32_t color_coding_idx;
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

/** Find an image which we can write into. Returns -1 if no frame is
 * currently available; you'll have to drop data. Used by CALLBACK
 * thread. **/
static int get_empty_frame(impl_pgusb_t *impl)
{
    int idx = -1;
    pthread_mutex_lock(&impl->queue_mutex);
    for (int i = 0; i < impl->nimages; i++) {
        if (impl->images[i].status == 0) {
            idx = i;
            impl->images[i].status = -1;
            break;
        }
    }

    pthread_mutex_unlock(&impl->queue_mutex);
    return idx;
}

/** Return a frame buffer to the pool. Called by release_frame **/
static void put_empty_frame(impl_pgusb_t *impl, int idx)
{
    assert(idx >= 0);

    pthread_mutex_lock(&impl->queue_mutex);
    assert(impl->images[idx].status == -2);
    impl->images[idx].status = 0;
    pthread_mutex_unlock(&impl->queue_mutex);
}

/** An image is now ready for return to the user. **/
static void put_ready_frame(impl_pgusb_t *impl, int idx)
{
    pthread_mutex_lock(&impl->queue_mutex);

    assert(idx >= 0);
    assert(impl->images[idx].status == -1);

    int maxstatus = 0;

    // where in the queue should it go?
    for (int i = 0; i < impl->nimages; i++) {
        if (impl->images[i].status > maxstatus)
            maxstatus = impl->images[i].status;
    }

    assert(maxstatus <= impl->nimages);
    impl->images[idx].status = maxstatus + 1;

    pthread_cond_broadcast(&impl->queue_cond);
    pthread_mutex_unlock(&impl->queue_mutex);
}

/** Retrieve an image for the user, waiting if necessary. **/
static int get_ready_frame(impl_pgusb_t *impl)
{
    pthread_mutex_lock(&impl->queue_mutex);

    while (1) {
        // do we have a frame ready to go?
        for (int i = 0; i < impl->nimages; i++) {
            if (impl->images[i].status == 1) {
                // yes!
                impl->images[i].status = -2;

                // move any other frames up in the queue.
                for (int j = 0; j < impl->nimages; j++) {
                    if (impl->images[j].status >= 1)
                        impl->images[j].status--;
                }

                pthread_mutex_unlock(&impl->queue_mutex);
                return i;
            }
        }

        pthread_cond_wait(&impl->queue_cond, &impl->queue_mutex);
    }
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

    if (debug)
        printf("DO_WRITE %08x := %08x\n", address, quads[0]);

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
        if (ret < 1) {
            printf("read_config_rom failed with ret=%d on word %d\n", ret, i);
            break;
        }
    }

    libusb_close(handle);
    return i;
}

static uint64_t get_guid(libusb_device *dev)
{
    uint32_t config[5];

    if (read_config_rom(dev, config, 5) < 5) {
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

            if (guid != 0xffffffffffffffffULL) {
                char buf[1024];
                snprintf(buf, 1024, "pgusb://%"PRIx64, guid);
                urls = string_array_add(urls, buf);
            }
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
    // don't forget: feature index starts at 0
    return 0;
}

static void stop_callback(struct libusb_transfer *transfer)
{
    free(transfer->buffer);
    libusb_free_transfer(transfer);
}

static void callback(struct libusb_transfer *transfer)
{
    image_source_t *isrc = (image_source_t*) transfer->user_data;
    assert(isrc->impl_type == IMPL_TYPE);
    impl_pgusb_t *impl = (impl_pgusb_t*) isrc->impl;
    image_source_format_t *ifmt = get_format(isrc, get_current_format(isrc));

    if (impl->current_frame_index < 0) {
        impl->current_frame_index = get_empty_frame(impl);
    }

    if (transfer->status == LIBUSB_TRANSFER_COMPLETED && impl->current_frame_index >= 0) {

        if (debug) {
            printf("TRANSFER flags %03d, status %03d, length %5d, actual %5d, current_frame_offset %10d / %d\n",
                   transfer->flags, transfer->status, transfer->length,
                   transfer->actual_length, impl->current_frame_offset, impl->bytes_transferred_per_frame);
        }

        int cpysz = impl->bytes_transferred_per_frame - impl->current_frame_offset;
        if (cpysz > transfer->actual_length)
            cpysz = transfer->actual_length;

        memcpy(&impl->images[impl->current_frame_index].buf[impl->current_frame_offset], transfer->buffer, cpysz);
        impl->current_frame_offset += cpysz;

        if (impl->current_frame_offset == impl->bytes_transferred_per_frame) {
            put_ready_frame(impl, impl->current_frame_index);

            // If there's more data available, stuff it into the next
            // frame.  NOTE: We don't like to do this; this means that
            // the image was a multiple of 512, and as a consequence,
            // the camera didn't generate a short USB frame. It's the
            // short USB frames that help us recover camera
            // synchronization; without them, we can only guess that
            // we've maintained synchronization.
            impl->current_frame_index = get_empty_frame(impl);
            impl->current_frame_offset = 0;

            if (impl->current_frame_index >= 0) {
                memcpy(impl->images[impl->current_frame_index].buf, &transfer->buffer[cpysz], transfer->actual_length - cpysz);
                impl->current_frame_offset += transfer->actual_length - cpysz;
            }
        }

        if (transfer->actual_length != transfer->length) {
//        if (transfer->actual_length == 512) {
            // we lost sync. start over.
            if (impl->current_frame_offset != 0)
                printf("sync: current_frame_offset %8d, transfer_size %8d, cpysz %8d, bytes_transferred_per_frame %8d\n",
                       impl->current_frame_offset, transfer->actual_length, cpysz, impl->bytes_transferred_per_frame);

//            put_ready_frame(impl, impl->current_frame_index);
//            impl->current_frame_index = -1;
//            impl->current_frame_offset = (impl->current_frame_offset + 512) % impl->bytes_transferred_per_frame;
            impl->current_frame_offset = 0;
        }

    } else {
        if (impl->current_frame_index < 0)
            printf("Ran out of frame buffers\n");
        else
            printf("ERROR    flags %03d, status %03d, length %5d, actual %5d, current_frame_offset %10d / %d, current_frame_index %d\n",
                   transfer->flags, transfer->status, transfer->length, transfer->actual_length,
                   impl->current_frame_offset, impl->bytes_transferred_per_frame, impl->current_frame_index);
    }

    pthread_mutex_lock(&impl->pending_transaction_mutex);

    impl->transfers_submitted--;

    if (!impl->consume_transfers_flag) {
        // resubmit the transfer.
        libusb_fill_bulk_transfer(transfer, impl->handle,
                                  0x81, transfer->buffer, impl->transfer_size, callback, isrc, TIMEOUT_MS);

        if (libusb_submit_transfer(transfer) < 0) {
            printf("***** UH OH ****** submit failed\n");
        }

        impl->transfers_submitted++;
    }
    pthread_mutex_unlock(&impl->pending_transaction_mutex);
}

static void *worker_thread_proc(void *arg)
{
    image_source_t *isrc = (image_source_t*) arg;
    impl_pgusb_t *impl = (impl_pgusb_t*) isrc->impl;

    while (!impl->worker_exit_flag) {

        struct timeval tv = {
            .tv_sec = 0,
            .tv_usec = 100000,
        };

        libusb_handle_events_timeout(impl->context, &tv);
    }

    return NULL;
}

static void do_value_setting(image_source_t *isrc)
{
    impl_pgusb_t *impl = (impl_pgusb_t*) isrc->impl;
    image_source_format_t *ifmt = get_format(isrc, get_current_format(isrc));
    struct format_priv *format_priv = (struct format_priv*) ifmt->priv;

    uint32_t quads[] = { 0x40000000 };

    if (do_write(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x7c, quads, 1) != 1)
        printf("failed write: line %d\n", __LINE__);

    while (1) {
        uint32_t resp;

        do_read(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x7c, &resp, 1);

        if (debug)
            printf("do_value_setting result: %08x\n", resp);

        if (resp & 0x80000000)
            break;
    }
}

static int start(image_source_t *isrc)
{
    assert(isrc->impl_type == IMPL_TYPE);
    impl_pgusb_t *impl = (impl_pgusb_t*) isrc->impl;
    image_source_format_t *ifmt = get_format(isrc, get_current_format(isrc));
    struct format_priv *format_priv = (struct format_priv*) ifmt->priv;

//    printf("CURRENT FORMAT %d\n", get_current_format(isrc));
//    printf("FORMAT_PRIV: %d %d %d\n", format_priv->format7_mode_idx, format_priv->color_coding_idx, format_priv->csr);

    int bytes_per_pixel = 1;
    if ((format_priv->color_coding_idx >=5 && format_priv->color_coding_idx <= 8) ||
        (format_priv->color_coding_idx == 10)) {
        bytes_per_pixel = 2;
    }

    impl->bytes_per_frame = ifmt->width * ifmt->height * bytes_per_pixel;

    // set iso channel
    if (1) {
        uint32_t quads[] = { 0x02000000 }; //
        if (do_write(impl->handle, CONFIG_ROM_BASE + impl->command_regs_base + 0x60c, quads, 1) != 1)
            printf("failed write: line %d\n", __LINE__);
    }
    do_value_setting(isrc);

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
        printf("FORMAT7_MODE_IDX %d\n", format_priv->format7_mode_idx);

        uint32_t quads[] = { format_priv->format7_mode_idx << 29 };
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
    do_value_setting(isrc);

    // set image position a08 = 00000000
    if (1) {
        uint32_t quads[] = { 0 };
        if (do_write(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x08, quads, 1) != 1)
            printf("failed write: line %d\n", __LINE__);
    }
    do_value_setting(isrc);

    // set format 7 color mode (a10 = 00000000)
    if (1) {
        uint32_t quads[] = { format_priv->color_coding_idx<<24 };
        if (do_write(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x10, quads, 1) != 1)
            printf("failed write: line %d\n", __LINE__);
    }

    // a7c = 40000000
    do_value_setting(isrc);

    if (1) {
        uint32_t pixels_per_frame;
        do_read(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x34, &pixels_per_frame, 1);

        uint32_t total_bytes_hi, total_bytes_lo;
        do_read(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x38, &total_bytes_hi, 1);
        do_read(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x3c, &total_bytes_lo, 1);

        uint32_t packets_per_frame;
        do_read(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x48, &packets_per_frame, 1);

        assert(total_bytes_hi == 0);

        printf("pixels_per_frame %8d, total_bytes_lo %8d, packets_per_frame %8d\n",
               pixels_per_frame, total_bytes_lo, packets_per_frame);

    }

    do_value_setting(isrc);

    // a44 = 0bc00000
    if (1) {
        uint32_t tmp;

        do_read(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x40, &tmp, 1);
        uint32_t packet_unit = (tmp >> 16) & 0xffff;
        uint32_t packet_max = (tmp) & 0xffff;

        // set packet size
        do_read(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x44, &tmp, 1);
        uint32_t previous_packet_size = (tmp >> 16) & 0xffff;
        uint32_t recommended_packet_size = (tmp) & 0xffff;

        printf("previous_packet_size %5d, recommended_packet_size %5d, packet_unit %5d, packet_max %5d\n",
               previous_packet_size, recommended_packet_size, packet_unit, packet_max);

        // pick a packet size that guarantees that the
        // bytes_transferred_per_frame will NOT be a multiple of
        // 512. This is important, since it will allow us to reliably
        // detect end-of-frame conditions.

        impl->packet_size = packet_max;
        while (1) {
            int npackets = (impl->bytes_per_frame + impl->packet_size - 1) / impl->packet_size;
            int total_bytes = npackets * impl->packet_size;
            int residual = total_bytes % 512;

            printf("** Packet size %d, npackets %d, total bytes %d, residual %d\n",
                   impl->packet_size, npackets, total_bytes, residual);

            if (residual == 0)
                impl->packet_size -= packet_unit;
            else
                break;

            if (impl->packet_size <= 0) {
                printf("Couldn't find a good packet size.\n");
                exit(1);
            }
        }
        assert(impl->packet_size % packet_unit == 0);

        printf("Picking a packet size of %d (unit = %d, max = %d)\n", impl->packet_size, packet_unit, packet_max);

        uint32_t quads[] = { (impl->packet_size)<<16 };

        if (do_write(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x44, quads, 1) != 1)
            printf("failed write: line %d\n", __LINE__);

        do_value_setting(isrc);

        do_read(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x44, &tmp, 1);
        uint32_t updated_packet_size = (tmp >> 16) & 0xffff;
        uint32_t updated_recommended_packet_size = (tmp) & 0xffff;

        if (updated_packet_size != impl->packet_size) {
            printf("WARNING: Wasn't able to change packet size from %d to %d; it's %d\n",
                   previous_packet_size, impl->packet_size, updated_packet_size);

            impl->packet_size = updated_packet_size;
        }
    }

    // check errorflag_2
//    do_value_setting(isrc);

   if (1) {
        uint32_t pixels_per_frame;
        do_read(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x34, &pixels_per_frame, 1);

        uint32_t total_bytes_hi, total_bytes_lo;
        do_read(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x38, &total_bytes_hi, 1);
        do_read(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x3c, &total_bytes_lo, 1);

        uint32_t packets_per_frame;
        do_read(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x48, &packets_per_frame, 1);

        uint32_t packet_tmp;
        do_read(impl->handle, CONFIG_ROM_BASE + format_priv->csr + 0x44, &packet_tmp, 1);
        uint32_t packet_size = (packet_tmp >> 16) & 0xffff;

        if (debug) {
            printf("READBACK: packet_size %8d, pixels_per_frame %8d, total_bytes_lo %8d, packets_per_frame %8d\n",
                   packet_size & 0xffff,
                   pixels_per_frame, total_bytes_lo, packets_per_frame);
        }

        assert((packet_size&0xffff) == impl->packet_size);
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

    impl->ntransfers = 100;
    impl->transfers = (struct transfer_info*) calloc(impl->ntransfers, sizeof(struct transfer_info));

    uint32_t packets_per_frame = (impl->bytes_per_frame + impl->packet_size - 1) / impl->packet_size;
    impl->bytes_transferred_per_frame = packets_per_frame * impl->packet_size;

    impl->transfer_size = 16384; // 16384 is largest size not split up by libusb

    printf("bytes_per_frame %d, bytes_transferred_per_frame %d, packets_per_frame %d\n",
           impl->bytes_per_frame, impl->bytes_transferred_per_frame, packets_per_frame);

    impl->transfers_submitted = 0;

    for (int i = 0; i < impl->ntransfers; i++) {
        impl->transfers[i].transfer = libusb_alloc_transfer(0);
        impl->transfers[i].buf = malloc(impl->transfer_size);
        libusb_fill_bulk_transfer(impl->transfers[i].transfer, impl->handle,
                                  0x81, impl->transfers[i].buf, impl->transfer_size, callback, isrc, TIMEOUT_MS);

        impl->transfers[i].transfer->flags = 0; //LIBUSB_TRANSFER_SHORT_NOT_OK;

        impl->transfers_submitted++;

        if (libusb_submit_transfer(impl->transfers[i].transfer) < 0) {
            printf("submit failed\n");
        }
    }

    impl->nimages = 10;
    impl->images = (struct image_info*) calloc(impl->nimages, sizeof(struct image_info));

    for (int i = 0; i < impl->nimages; i++) {
        impl->images[i].buf = malloc(impl->bytes_transferred_per_frame);
    }

    // we haven't lost any data yet.
    impl->current_frame_index = -1;
    impl->current_frame_offset = 0;

    pthread_mutex_lock(&impl->pending_transaction_mutex);

    impl->consume_transfers_flag = 0;
    impl->worker_exit_flag = 0;

    pthread_mutex_unlock(&impl->pending_transaction_mutex);

    // set transmission to ON
    if (1) {
        uint32_t quads[] = { 0x80000000UL };
        if (do_write(impl->handle, CONFIG_ROM_BASE + impl->command_regs_base + 0x614, quads, 1) != 1)
            printf("transmission on: ack!\n");
    }

    if (pthread_create(&impl->worker_thread, NULL, worker_thread_proc, isrc) != 0) {
        perror("pthread");
        return -1;
    }

    // we now are using the asynchronous libusb API.

    return 0;
}

static int get_frame(image_source_t *isrc, void **imbuf, int *buflen)
{
    assert(isrc->impl_type == IMPL_TYPE);
    impl_pgusb_t *impl = (impl_pgusb_t*) isrc->impl;
    image_source_format_t *ifmt = get_format(isrc, get_current_format(isrc));

    int idx = get_ready_frame(impl);

    *buflen = impl->bytes_per_frame;
    *imbuf = impl->images[idx].buf;

//    printf("get_frame\n");
    return 0;
}

static int release_frame(image_source_t *isrc, void *imbuf)
{
    assert(isrc->impl_type == IMPL_TYPE);
    impl_pgusb_t *impl = (impl_pgusb_t*) isrc->impl;

    int idx = -1;
    for (int i = 0; i < impl->nimages; i++) {
        if (impl->images[i].buf == imbuf) {
            idx = i;
            break;
        }
    }

    assert(idx >= 0);

    put_empty_frame(impl, idx);

    return 0;
}

static int stop(image_source_t *isrc)
{
    printf("STOPPING\n");
    assert(isrc->impl_type == IMPL_TYPE);
    impl_pgusb_t *impl = (impl_pgusb_t*) isrc->impl;

    impl->consume_transfers_flag = 1;

    if (1) {
        uint8_t *stop_buffer = calloc(1, LIBUSB_CONTROL_SETUP_SIZE + 4);
        // note: actual command is zero, so just call calloc.

        uint64_t address = CONFIG_ROM_BASE + impl->command_regs_base + 0x614;
        int request = address_to_request(address);

        struct libusb_transfer *stop_transfer = libusb_alloc_transfer(0);
        libusb_fill_control_setup(stop_buffer, 0x40, request, address & 0xffff, (address >> 16) & 0xffff, 4);

        libusb_fill_control_transfer(stop_transfer, impl->handle, stop_buffer, stop_callback, NULL, 0);
        libusb_submit_transfer(stop_transfer);
    }

    for (int i = 0; i < impl->ntransfers; i++) {
        libusb_cancel_transfer(impl->transfers[i].transfer);
    }

    while (1) {
        pthread_mutex_lock(&impl->pending_transaction_mutex);
        int cnt = impl->transfers_submitted;
        pthread_mutex_unlock(&impl->pending_transaction_mutex);

        if (cnt == 0)
            break;

        printf("waiting for transactions to expire: %d\n", cnt);

        usleep(50000);
    }

    impl->worker_exit_flag = 1;
    pthread_join(impl->worker_thread, NULL);

    printf("Worker thread exited\n");

    // We're now using the synchronous API again.

    // set transmission to OFF.
    if (1) {
        uint32_t quads[] = { 0x00000000UL };
        if (do_write(impl->handle, CONFIG_ROM_BASE + impl->command_regs_base + 0x614, quads, 1) != 1)
            printf("transmission off: ack!\n");
    }

    for (int i = 0; i < impl->ntransfers; i++) {
        libusb_free_transfer(impl->transfers[i].transfer);
        free(impl->transfers[i].buf);
    }

    free(impl->transfers);

    for (int i = 0; i < impl->nimages; i++) {
        free(impl->images[i].buf);
    }
    free(impl->images);

    libusb_release_interface(impl->handle, 0);

    return 0;
}

static int my_close(image_source_t *isrc)
{
    assert(isrc->impl_type == IMPL_TYPE);
    impl_pgusb_t *impl = (impl_pgusb_t*) isrc->impl;

    libusb_close(impl->handle);
    libusb_exit(impl->context);

    return 0;
}

image_source_t *image_source_pgusb_open(url_parser_t *urlp)
{
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
    pthread_mutex_init(&impl->pending_transaction_mutex, NULL);

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

                uint32_t cmodes = quads[5];
                for (int cmode = 0; cmode < 11; cmode++) {
                    if (cmodes & (1<<(31-cmode))) {
                        printf(" %d %s\n", cmode, COLOR_MODES[cmode]);

                        impl->formats = realloc(impl->formats, (impl->nformats+1) * sizeof(image_source_format_t*));
                        impl->formats[impl->nformats] = calloc(1, sizeof(image_source_format_t));
                        impl->formats[impl->nformats]->height = quads[0] & 0xffff;
                        impl->formats[impl->nformats]->width = quads[0] >> 16;

                        if (cmode == 9) {
                            // 8 bit bayer
                            int filter_mode = quads[22]>>24;
                            impl->formats[impl->nformats]->format = strdup(BAYER_MODES[filter_mode]);
                        } else if (cmode == 10) {
                            // 16 bit bayer
                            int filter_mode = quads[22]>>24;
                            char buf[1024];
                            sprintf(buf, "%s16", BAYER_MODES[filter_mode]);
                            impl->formats[impl->nformats]->format = strdup(buf);
                        } else {
                            // not a bayer
                            impl->formats[impl->nformats]->format = strdup(COLOR_MODES[cmode]);
                        }

                        struct format_priv *format_priv = calloc(1, sizeof(struct format_priv));
                        printf("MODE %d\n", mode);

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

    return isrc;
}

