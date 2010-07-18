#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <pthread.h>
#include <signal.h>
#include <sys/time.h>
#include <math.h>

#include "scip2.h"
#include "common/getopt.h"
#include "common/timestamp.h"
#include "common/timespec.h"
#include "common/vhash.h"

typedef struct state state_t;
struct state
{
    getopt_t *gopt;
    scip2_t *scip;

    vhash_t *properties; // responses from PP, VV, II commands
};

void do_reset(state_t *state)
{
    varray_t *response = scip2_transaction(state->scip, "RS", NULL, NULL, 0);
    scip2_response_free(state->scip, response);
}

// My UTM-30LX seems to only support SCIP2.0 mode; sending SCIP2.0
// returns an error code (0x0e).
void do_scip2_mode(state_t *state)
{
    varray_t *response = scip2_transaction(state->scip, "SCIP2.0", NULL, NULL, 0);
    scip2_response_free(state->scip, response);
}

// Decode responses from PP, VV, II commands and insert key/value
// pairs into properties hash table.
void decode_properties(state_t *state, varray_t *response)
{
    if (varray_size(response) < 2) {
        printf("Invalid response to properties command\n");
        exit(1);
    }

    char *v = varray_get(response, 1);
    if (strncmp(v, "00", 2)) {
        printf("Failure on properties command\n");
        exit(1);
    }

    // break a line like "DMAX:60000;J" into key/value pairs.
    for (int i = 2; i < varray_size(response); i++) {
        char *line = varray_get(response, i);
        char *colon = index(line, ':');
        char *semi = index(line, ';'); // don't use rindex, because ';' can occur as the checksum.

        if (colon == NULL || semi == NULL)
            continue;

        char *key = strndup(line, colon - line);
        char *value = strndup(colon+1, semi-colon-1);

        if (vhash_get(state->properties, key)) {
            // free old value and new key.
            free(vhash_get(state->properties, key));
            vhash_put(state->properties, key, value);
            free(key);
        } else {
            vhash_put(state->properties, key, value);
        }
    }
}

// query various information from the sensor
void do_get_info(state_t *state)
{
    if (1) {
        varray_t *response = scip2_transaction(state->scip, "VV", NULL, NULL, 0);
        decode_properties(state, response);
        scip2_response_free(state->scip, response);
    }

    if (1) {
        varray_t *response = scip2_transaction(state->scip, "PP", NULL, NULL, 0);
        decode_properties(state, response);
        scip2_response_free(state->scip, response);
    }

    if (1) {
        varray_t *response = scip2_transaction(state->scip, "II", NULL, NULL, 0);
        decode_properties(state, response);
        scip2_response_free(state->scip, response);
    }
}

static int on_md_data(varray_t *response, void *_a)
{
    state_t *state = (state_t*) _a;

    printf("Got one!\n");

    scip2_response_free(state->scip, response);

    return 0;
}

int main(int argc, char *argv[])
{
    state_t *state = (state_t*) calloc(1, sizeof(state_t));

    setlinebuf (stdout);
    state->gopt = getopt_create();

    getopt_add_bool(state->gopt, 'h',"help", 0,"Show this");

    getopt_add_string(state->gopt, 'c', "channel", "LASER", "LC channel name");

    getopt_add_spacer(state->gopt, "");
    getopt_add_string(state->gopt,'d',  "device",          "/dev/ttyACM0",  "Device to connect to");

    getopt_add_bool(state->gopt, '\0',"scip-debug", 0,"Show SCIP communications");
    getopt_add_bool(state->gopt, '\0',"time-test", 0, "Measure clock drift");

    if (!getopt_parse(state->gopt, argc, argv, 1) || getopt_get_bool(state->gopt,"help")
        || state->gopt->extraargs->len!=0) {

        printf("Usage: %s [options]\n\n", argv[0]);
        getopt_do_usage(state->gopt);
        return 0;
    }

    state->scip = scip2_create(getopt_get_string(state->gopt, "device"));
    if (state->scip == NULL) {
        perror(getopt_get_string(state->gopt, "device"));
        return 1;
    }

    state->scip->debug = getopt_get_bool(state->gopt, "scip-debug");

    state->properties = vhash_create(vhash_str_hash, vhash_str_equals);
    vhash_t *propdesc = vhash_create(vhash_str_hash, vhash_str_equals);
    vhash_put(propdesc, "ARES", "Number of angular steps per 360deg");
    vhash_put(propdesc, "AMIN", "Minimum angular step index");
    vhash_put(propdesc, "AMAX", "Maximum angular step index");
    vhash_put(propdesc, "AFRT", "Angular index pointing forward");
    vhash_put(propdesc, "DMAX", "Maximum range (mm)");
    vhash_put(propdesc, "DMIN", "Minimum range (mm)");
    vhash_put(propdesc, "FIRM", "Firmware version");
    vhash_put(propdesc, "MODL", "Model number");
    vhash_put(propdesc, "LASR", "Laser power status");
    vhash_put(propdesc, "MESM", "Measurement mode");
    vhash_put(propdesc, "STAT", "Sensor health status");
    vhash_put(propdesc, "SERI", "Serial number");
    vhash_put(propdesc, "PROD", "Product number");
    vhash_put(propdesc, "PROT", "Protocol Revision");
    vhash_put(propdesc, "SCAN", "Scan rate (rpm)");
    vhash_put(propdesc, "SCSP", "Current scan rate (rpm)");
    vhash_put(propdesc, "SBPS", "Serial bits per second");
    vhash_put(propdesc, "TIME", "Internal time stamp (ms)");
    vhash_put(propdesc, "VEND", "Vendor");

    do_reset(state);

//    do_scip2_mode(state);

    do_get_info(state);

    // Display properties
    if (1) {
        vhash_iterator_t vit;
        vhash_iterator_init(state->properties, &vit);
        void *key = NULL;
        while ((key = vhash_iterator_next_key(state->properties, &vit)) != NULL) {
            printf("%35s (%s) = %s\n",(char*) vhash_get(propdesc, key), (char*) key, (char*) vhash_get(state->properties, key));
        }
    }

    if (getopt_get_bool(state->gopt, "time-test")) {
        // This is a crude test for measuring the accuracy of the
        // hokuyo's clock. Note that it does NOT handle the 24bit
        // wrap-around of the clock.
        //
        // Empirically, UTM-30LX seems to have a quartz-quality clock,
        // with drift < 0.01%
        double t0 = timestamp_now() / 1000000.0;
        double s0 = strtol(vhash_get(state->properties, "TIME"), NULL, 16) / 1000.0;

        double delta0 = t0 - s0;

        while (1) {
            do_get_info(state);

            double t = timestamp_now() / 1000000.0;
            double s = strtol(vhash_get(state->properties, "TIME"), NULL, 16) / 1000.0;

            double delta = t - s;

            printf("delta: %15f, delta rate: %15f, s: %15s\n", (delta - delta0), (delta - delta0) / (t - t0), (char*) vhash_get(state->properties, "TIME"));

            usleep(100000);
        }
    }

    char cmd[1024];
    sprintf(cmd, "MD%04d%04d%02d%1d%2d",
            atoi(vhash_get(state->properties, "AMIN")),
            atoi(vhash_get(state->properties, "AMAX")),
            0, // cluster count
            0, // scan interval
            10); // scan count

    varray_t *response = scip2_transaction(state->scip, cmd, on_md_data, state, 0);
    while (1) {
        sleep(1);
    }
}
