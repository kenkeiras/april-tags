/* C port of april.util.TimeSync */

#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <math.h>

#include "timesync.h"

/*
   device_ticks_per_second: How fast does the counter on the device count, in Hz?
   device_ticks_wrap: After how many ticks does the device counter "roll over"?
                      Use 0 if it does not roll over.
   rate_error: What is the rate error? (usually a small number like 0.01)
   reset_time: Force a resynchronization if the sync error exceeds this many seconds.
*/
timesync_t* timesync_create(double device_ticks_per_second,
                            int64_t device_ticks_wrap,
                            double rate_error,
                            double reset_time)
{
    timesync_t *ts = calloc(1, sizeof(timesync_t));
    if (ts != NULL) {
        ts->device_ticks = -1LL;
        ts->device_ticks_per_second = device_ticks_per_second;
        ts->device_ticks_wrap = device_ticks_wrap;
        ts->rate_error = rate_error;
        ts->reset_time = reset_time;
    }
    return ts;
}

void timesync_destroy(timesync_t *ts)
{
    free(ts);
}

/*
    host_utime: time of arrival
    device_ticks: time of message timestamp
*/
void timesync_update(timesync_t *ts, int64_t host_utime, int64_t device_ticks)
{
    // Check for wraparound
    if (device_ticks < ts->last_device_ticks) {
        ts->device_ticks_offset += ts->device_ticks_wrap;
    }
    ts->last_device_ticks = device_ticks;

    device_ticks += ts->device_ticks_offset;

    double dp = (device_ticks - ts->device_ticks) / ts->device_ticks_per_second;
    double dq = (host_utime - ts->host_ticks) / 1e6;

    ts->last_sync_error = fabs(dp - dq);

    if (ts->device_ticks == -1LL || ts->last_sync_error >= ts->reset_time) {
        // Resynchronize
        ts->device_ticks = device_ticks;
        ts->host_ticks = host_utime;
        
        ts->resync_count++;
        return;
    }

    if (dp >= dq - fabs(ts->rate_error * dp)) {
        ts->device_ticks = device_ticks;
        ts->host_ticks = host_utime;
    }
}

int64_t timesync_get_host_utime(timesync_t *ts, int64_t device_ticks)
{
    device_ticks += ts->device_ticks_offset;

    // Check if asking about a timestamp from the previous epoch
    // (before device counter wrapped)
    if (device_ticks > ts->last_device_ticks)
        device_ticks -= ts-> device_ticks_wrap;

    double dp = (device_ticks - ts->device_ticks) / ts->device_ticks_per_second;
    return ((int64_t)(dp*1e6)) + ts->host_ticks +
        ((int64_t)(1e6*fabs(ts->rate_error * dp)));
}
