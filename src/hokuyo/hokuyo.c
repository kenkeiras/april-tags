#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <getopt.h>
#include <sys/time.h>
#include <time.h>

#include <glib.h>
#include <lcm/lcm.h>

#include "lcmtypes/laser_t.h"
#include <common/timestamp.h>

#include <pthread.h>
#include <unistd.h>

#define MAX_ACM_DEVS 20

#define TO_DEGREES(rad) ((rad)*180/M_PI)

#include "liburg/urg_ctrl.h"

static void
usage(const char *progname)
{
    fprintf (stderr, "usage: %s [options]\n"
            "\n"
            "  -h, --help             shows this help text and exits\n"
            "  -c, --channel CHAN     LCM channel name\n"
            "  -d, --device DEV       Device file to connect to\n"
            "  -i, --id ID            Search for Hokuyo with serial id ID\n"
            "  -l, --lcmurl URL       LCM URL\n"
            , g_path_get_basename(progname));
}
/*
static inline int64_t
_timestamp_now()
{
    struct timeval tv;
    gettimeofday (&tv, NULL);
    return (int64_t) tv.tv_sec * 1000000 + tv.tv_usec;
}
*/
static char **
_get_acm_devnames(void)
{
    char **result = (char**)calloc(1, (MAX_ACM_DEVS+1)*sizeof(char*));
    int n = 0;
    for (int i=0;i<MAX_ACM_DEVS;i++) {
        char devname[256];
        sprintf(devname,"/dev/ttyACM%d",i);
        if(g_file_test(devname, G_FILE_TEST_EXISTS)) {
            result[n] = g_strdup(devname);
            n++;
        }
    }
    return result;
}

static int
_connect_by_device(urg_t *urg, urg_parameter_t *params, const char *device)
{
    if(urg_connect(urg, device, 115200) < 0) {
        return 0;
    }
    urg_parameters(urg, params);
    return 1;
}

static int
_connect_any_device(urg_t *urg, urg_parameter_t *params)
{
    char **devnames = _get_acm_devnames();
    if(!devnames[0]) {
        printf("No Hokuyo detected\n");
    }
    for(int i=0; devnames[i]; i++) {
        printf("Trying %s...\n", devnames[i]);
        if(_connect_by_device(urg, params, devnames[i])) {
            g_strfreev(devnames);
            return 1;
        }
    }
    g_strfreev(devnames);
    return 0;
}

static gboolean
_read_serialno(urg_t *urg, int *serialno)
{
    // read the serial number of the hokuyo.  This is buried within
    // a bunch of other crap.
    int LinesMax = 5;
    char version_buffer[LinesMax][UrgLineWidth];
    char *version_lines[LinesMax];
    for (int i = 0; i < LinesMax; ++i) {
        version_lines[i] = version_buffer[i];
    }
    int status = urg_versionLines(urg, version_lines, LinesMax);
    if (status < 0) {
        fprintf(stderr, "urg_versionLines: %s\n", urg_error(urg));
        return 0;
    }
    const char *prefix = "SERI:";
    int plen = strlen(prefix);

    for(int i = 0; i < LinesMax; ++i) {
        if(!strncmp(version_lines[i], prefix, plen)) {
            char *eptr = NULL;
            int sn = strtol(version_lines[i] + plen, &eptr, 10);
            if(eptr != version_lines[i] + plen) {
                *serialno = sn;
                return 1;
            }
        }
    }
    return 0;
}

static gboolean
_connect_by_id(urg_t *urg, urg_parameter_t *params, const int desired_serialno)
{
    char **devnames = _get_acm_devnames();
    for(int i=0; devnames[i]; i++) {
        printf("Trying %s...\n", devnames[i]);
        const char *devname = devnames[i];

        if(!_connect_by_device(urg, params, devname)) {
            continue;
        }

        int serialno = -1;
        if(!_read_serialno(urg, &serialno)) {
            printf("Couldn't read serial number on %s\n", devname);
            urg_laserOff(urg);
            urg_disconnect(urg);
            continue;
        }

        if(desired_serialno == serialno) {
            printf("Found %d on %s\n", serialno, devname);
            g_strfreev(devnames);
            return 1;
        } else {
            printf("Skipping %s (found serial #: %d, desired: %d)\n", devname, serialno, desired_serialno);
            urg_laserOff(urg);
            urg_disconnect(urg);
        }
    }
    g_strfreev(devnames);
    return 0;
}

static gboolean
_connect(urg_t *urg, urg_parameter_t *params, int serialno,
        const char *device, int *data_max)
{
    if(serialno) {
        if(!_connect_by_id(urg, params, serialno)) {
            return 0;
        }
    } else if(device) {
        if(!_connect_by_device(urg, params, device))
            return 0;
    } else {
        if(!_connect_any_device(urg, params)) {
            return 0;
        }
    }

    if(data_max)
        *data_max = urg_dataMax(urg);

    // read and print out version information
    int LinesMax = 5;
    char version_buffer[LinesMax][UrgLineWidth];
    char *version_lines[LinesMax];
    for (int i = 0; i < LinesMax; ++i) {
        version_lines[i] = version_buffer[i];
    }
    int status = urg_versionLines(urg, version_lines, LinesMax);
    if (status < 0) {
        fprintf(stderr, "urg_versionLines: %s\n", urg_error(urg));
        urg_disconnect(urg);
        return 0;
    }
    for(int i = 0; i < LinesMax; ++i) {
        printf("%s\n", version_lines[i]);
    }
    printf("\n");

    // configure Hokuyo to continuous capture mode.
    urg_setCaptureTimes(urg, UrgInfinityTimes);

    // start data transmission
    status = urg_requestData(urg, URG_MD, URG_FIRST, URG_LAST);
    if (status < 0) {
        fprintf(stderr, "urg_requestData(): %s\n", urg_error(urg));
        urg_disconnect(urg);
        return 0;
    }

    return 1;
}

volatile int watchdog_got_scan = 0;

void *watchdog_task(void *arg)
{
    while (1) {
        watchdog_got_scan = 0;
        sleep(4);
        if (watchdog_got_scan == 0) {
            printf("Watchdog forcing exit.\n");
            exit(-1);
        }
    }
}

int main(int argc, char *argv[])
{
    setlinebuf(stdout);

    char *optstring = "hc:d:p:i:a";
    char c;
    struct option long_opts[] = {
        {"help", no_argument, 0, 'h'},
        {"channel", required_argument, 0, 'c'},
        {"device", required_argument, 0, 'd'},
        {"id", required_argument, 0, 'i'},
        {"lcmurl", required_argument, 0, 'l'},
        {0, 0, 0, 0}
    };

    int exit_code = 0;
    char *device = NULL;
    char *channel = g_strdup("HOKUYO_LIDAR");
    char *lcm_url = NULL;
    int serialno = 0;

    while ((c = getopt_long (argc, argv, optstring, long_opts, 0)) >= 0)
    {
        switch (c)
        {
            case 'c':
                free(channel);
                channel = g_strdup(optarg);
                break;
            case 'd':
                free(device);
                device = g_strdup(optarg);
                break;
            case 'p':
                free(lcm_url);
                lcm_url = g_strdup(optarg);
                break;
            case 'i':
                {
                    char *eptr = NULL;
                    serialno = strtol(optarg, &eptr, 10);
                    if(*eptr != '\0') {
                        usage(argv[0]);
                        return 1;
                    }
                }
                break;
            case 'h':
            default:
                usage(argv[0]);
                return 1;
        }
    }

    int data_max;
    long* data = NULL;
    urg_parameter_t urg_param;

    if (1) {
        pthread_t pt;
        pthread_create(&pt, NULL, watchdog_task, NULL);
    }

    // setup LCM
    lcm_t *lcm = lcm_create(lcm_url);
    if(!lcm) {
        fprintf(stderr, "Couldn't setup LCM\n");
        return 1;
    }

    urg_t urg;
    int max_initial_tries = 10;
    int connected = 0;
    for(int i=0; i<max_initial_tries && !connected; i++) {
        connected = _connect(&urg, &urg_param, serialno, device, &data_max);
        if(!connected) {
            struct timespec ts = { 0, 500000000 };
            nanosleep(&ts, NULL);
        }
    }
    if(!connected) {
        fprintf(stderr, "Unable to connect to any device\n");
        lcm_destroy(lcm);
        return 1;
    }

    // # of measurements per scan?
    data = (long*)malloc(sizeof(long) * data_max);
    if (data == NULL) {
        perror("data buffer");
        exit_code = 1;
        goto done;
    }

    laser_t msg;
    int max_nranges = urg_param.area_max_ - urg_param.area_min_ + 1;
    msg.ranges = (float*) malloc(sizeof(float) * max_nranges);
    msg.nintensities = 0;
    msg.intensities = NULL;
    msg.radstep = 2.0 * M_PI / urg_param.area_total_;
    msg.rad0 = (urg_param.area_min_ - urg_param.area_front_) * msg.radstep;

    printf("Angular resolution: %f deg\n", TO_DEGREES(msg.radstep));
    printf("Starting angle:     %f deg\n", TO_DEGREES(msg.rad0));
    printf("Scan RPM:           %d\n", urg_param.scan_rpm_);
    printf("\n");

    int64_t now = timestamp_now();
    int64_t report_last_utime = now;
    int64_t report_interval_usec = 2000000;
    int64_t next_report_utime = now + report_interval_usec;

    int64_t scancount_since_last_report = 0;
    int failure_count = 0;
    int reconnect_thresh = 10;
    int epic_fail = 0;
    int max_reconn_attempts = 600;

    timestamp_sync_state_t *sync = timestamp_sync_init(1000, 4294967296L, 1.001); // guessed at wrap-around based on 4 byte field.

    // loop forever, reading scans
    while(!epic_fail) {
        int nranges = urg_receiveData(&urg, data, data_max);
        now = timestamp_now();
        int64_t hokuyo_mtime = urg_recentTimestamp(&urg);

        if(nranges < 0) {
            // sometimes, the hokuyo can freak out a little.
            // Count how many times we've failed to get data from the hokuyo
            // If it's too many times, then reset the connection.  That
            // can help sometimes..
            fprintf(stderr, "urg_receiveData(): %s\n", urg_error(&urg));
            failure_count++;
            struct timespec ts = { 0, 300000000 };
            nanosleep(&ts, NULL);

            int reconn_failures = 0;
            while(failure_count > reconnect_thresh) {
                if(connected) {
                    urg_disconnect(&urg);
                    connected = 0;
                    fprintf(stderr, "Comms failure.  Trying to reconnect...\n");
                }

                if(_connect(&urg, &urg_param, serialno, device, NULL)) {
                    failure_count = 0;
                    connected = 1;
                }

                // Throttle reconnect attempts
                struct timespec ts = { 0, 500000000 };
                nanosleep(&ts, NULL);

                reconn_failures++;
                if(reconn_failures > max_reconn_attempts) {
                    fprintf(stderr, "Exceeded maximum reconnection attempts.\n");
                    exit_code = 1;
                    epic_fail = 1;
                    break;
                }
            }
            continue;
        }

        if(failure_count > 0)
            failure_count--;

        int64_t synced_now = timestamp_sync(sync, hokuyo_mtime, now);
        msg.utime = now; // XXX Timesync above misbehaves. Using this for now.

        double timesync_error = (now - synced_now)/1000000.0; // should always be >= 0

        msg.nranges = nranges;
        for(int i=0; i<nranges; i++) {
            msg.ranges[i] = data[i] * 1e-3;
        }

        laser_t_publish(lcm, channel, &msg);

        scancount_since_last_report++;
        watchdog_got_scan = 1;

        if(now > next_report_utime) {
            double dt = (now - report_last_utime) * 1e-6;

            printf("Hokuyo: %4.1f Hz  time sync: %f\n", scancount_since_last_report / dt, timesync_error);

            scancount_since_last_report = 0;
            next_report_utime = now + report_interval_usec;
            report_last_utime = now;
        }
    }

done:
    lcm_destroy(lcm);
    if(connected) {
        urg_laserOff(&urg);
        urg_disconnect(&urg);
    }

    free(data);
    free(lcm_url);
    free(channel);
    free(device);

    return exit_code;
}
