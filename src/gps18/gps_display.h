#ifndef _GPS_DISPLAY_H
#define _GPS_DISPLAY_H

#include <curses.h>
//#include <ncurses.h>
#include <wchar.h>

#define GPS_DISPLAY_NSATS 38
#define MAX_MSG_LEN 256
#define MAX_LOG_LINES 8

typedef struct gps_display gps_display_t;

struct gps_display
{
	WINDOW *w;
	WINDOW *lw; 
	
	double utc;

	double lat;
	double lon;

	double v, ve, vn, vu; // velocities
	double heading;

	double err_horiz;
	double err_vert;
	double err_pos;

	double *sat_elevation, *sat_elevation_b;
	double *sat_azimuth, *sat_azimuth_b;
	double *sat_snr, *sat_snr_b; // set to -1 for satellite unseen


	int    status;

	int    messagecount;
	char   log[MAX_LOG_LINES][MAX_MSG_LEN];
	int    logline[MAX_LOG_LINES];
	int    logidx;
};

gps_display_t *gps_display_create();
void gps_display_repaint(gps_display_t *gd);
void gps_log(gps_display_t *gd, const char *msg);
void gps_display_process_nmea(gps_display_t *gd, const char *nmea);
pthread_t gps_display_start(gps_display_t *gd);

#endif
