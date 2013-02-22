#ifndef __timesync_h
#define __timesync_h

#include <stdint.h>

typedef struct {
    int64_t last_device_ticks;
    int64_t device_ticks_offset;
    int64_t device_ticks_wrap;
    double device_ticks_per_second;
    double rate_error;
    double reset_time;

    int64_t device_ticks;
    int64_t host_ticks;

    // Statistics
    double last_sync_error;
    uint32_t resync_count;
} timesync_t;

timesync_t* timesync_create(double device_ticks_per_second,
                            int64_t device_ticks_wrap,
                            double rate_error,
                            double reset_time);
void timesync_destroy(timesync_t *ts);
void timesync_update(timesync_t *ts, int64_t host_utime, int64_t device_ticks);
int64_t timesync_get_host_utime(timesync_t *ts, int64_t device_ticks);

#endif
