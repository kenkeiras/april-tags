#include <string.h>
#include <stdint.h>
#include <stdlib.h>
#include <inttypes.h>
#include <stdio.h>

#include "image_source.h"
#include "url_parser.h"

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

image_source_t *image_source_open(const char *url)
{
    image_source_t *isrc = NULL;

    url_parser_t *urlp = url_parser_create(url);
    if (urlp == NULL) // bad URL format
        return NULL;

    const char *protocol = url_parser_get_protocol(urlp);
    const char *location = url_parser_get_location(urlp);

    if (!strcmp(protocol, "v4l2://"))
        isrc = image_source_v4l2_open(location);
    else if (!strcmp(protocol, "dc1394://")) {
        int64_t guid = 0;
        if (strto64(location, strlen(location), &guid)) {
            printf("image_source_open: dc1394 guid '%s' is not a valid integer.\n", &url[9]);
            return NULL;
        }
        isrc = image_source_dc1394_open(guid);
    }

    if (isrc != NULL) {
        int fidx = atoi(url_parser_get_parameter(urlp, "fidx", "0"));
        isrc->set_format(isrc, fidx);
    }

    url_parser_destroy(urlp);

    // don't know what to do!
    return isrc;
}

char** image_source_enumerate()
{
    char **urls = calloc(1, sizeof(char*));

    urls = image_source_enumerate_v4l2(urls);
    urls = image_source_enumerate_dc1394(urls);

    return urls;
}

void image_source_enumerate_free(char **urls)
{
    if (urls == NULL)
        return;

    for (int i = 0; urls[i] != NULL; i++)
        free(urls[i]);
    free(urls);
}
