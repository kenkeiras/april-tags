#include <stdio.h>
#include <stdlib.h>

//#include <ncurses.h>
#include <curses.h>
#include <wchar.h>
#include <stdarg.h>
#include <string.h>
#include <time.h>
#include <math.h>
#include <ctype.h>
#include <pthread.h>
#include <unistd.h>

#define COLOR_PLAIN 1
#define COLOR_TITLE 2
#define COLOR_ERROR 3
#define COLOR_WARN  4

#define GPS_STATUS_ERROR 0
#define GPS_STATUS_NO_LOCK 1
#define GPS_STATUS_LOCK 2
#define GPS_STATUS_DGPS_LOCK 3

#include "gps.h"
#include "gps_display.h"

gps_display_t *gps_display_create()
{
	gps_display_t *gd = (gps_display_t*) calloc(sizeof(gps_display_t), 1);

	gd->w = initscr();

	start_color();
	cbreak();
	noecho();

	init_pair(COLOR_PLAIN, COLOR_WHITE, COLOR_BLACK);
	init_pair(COLOR_TITLE, COLOR_BLACK, COLOR_BLUE);
	init_pair(COLOR_WARN, COLOR_BLACK, COLOR_YELLOW);
	init_pair(COLOR_ERROR, COLOR_BLACK, COLOR_RED);

	gd->sat_elevation = (double*) calloc(sizeof(double), GPS_DISPLAY_NSATS);
	gd->sat_azimuth   = (double*) calloc(sizeof(double), GPS_DISPLAY_NSATS);
	gd->sat_snr       = (double*) calloc(sizeof(double), GPS_DISPLAY_NSATS);

	gd->sat_elevation_b = (double*) calloc(sizeof(double), GPS_DISPLAY_NSATS);
	gd->sat_azimuth_b   = (double*) calloc(sizeof(double), GPS_DISPLAY_NSATS);
	gd->sat_snr_b       = (double*) calloc(sizeof(double), GPS_DISPLAY_NSATS);

	for (int i = 0; i < GPS_DISPLAY_NSATS; i++)
		gd->sat_snr[i] = -1;

	gd->status=GPS_STATUS_ERROR;

	printf("HERE\n");

	return gd;
}

void *gps_display_proc(void *_context)
{
//	gps_display_t *gd = (gps_display_t*) _context;

	while (1) {
		sleep(1);

	}

	return NULL;
}

pthread_t gps_display_start(gps_display_t *gd)
{
	pthread_t pid;

	printf("HERE_DS_A\n");
	pthread_create(&pid, NULL, gps_display_proc, gd);
	printf("HERE_DS_B\n");

	return pid;
}

void gps_display_sats(gps_display_t *gd)
{
	int width, height;

	getmaxyx(gd->w, height, width);
	color_set(COLOR_PLAIN, NULL);

	for (int i = 0; i < GPS_DISPLAY_NSATS; i++) {
		int x = (i >= 16) ? width-24 : width-48;
		int y = (i%16) + 2;
		wmove(gd->w, y, x);
		if (gd->sat_snr[i] >=0)
			wprintw(gd->w, "%2d : %2.0f dB (%2.0f,%3.0f)", i, gd->sat_snr[i], gd->sat_elevation[i], gd->sat_azimuth[i]);
		else
			wprintw(gd->w, "%2d : ---              ", i);
	}

	wmove(gd->w, height-1, width-1);
}

void gps_display_status(gps_display_t *gd)
{
	int width, height;

	getmaxyx(gd->w, height, width);
	color_set(COLOR_PLAIN, NULL);

	wmove(gd->w, 2, 0);
	color_set(COLOR_PLAIN, NULL);
	
	wprintw(gd->w, "Status: ");
	switch (gd->status)
	{
	case GPS_STATUS_ERROR:
		color_set(COLOR_ERROR, NULL);
		wprintw(gd->w, "      ERROR      ");
		break;
	case GPS_STATUS_NO_LOCK:
		color_set(COLOR_WARN, NULL);
		wprintw(gd->w, "     NO LOCK     ");
		break;
	case GPS_STATUS_LOCK:
		color_set(COLOR_PLAIN, NULL);
		wprintw(gd->w, "Locked (no DGPS) ");
		break;
	case GPS_STATUS_DGPS_LOCK:
		color_set(COLOR_PLAIN, NULL);
		wprintw(gd->w, " Locked (DGPS)   ");
		break;
	}
}

void gps_display_location(gps_display_t *gd)
{
	color_set(COLOR_PLAIN, NULL);
	wmove(gd->w, 3, 0);
	if (gd->status >= GPS_STATUS_LOCK) {
		wprintw(gd->w, "Accuracy: %.2f m", gd->err_pos);
		wmove(gd->w, 5, 0);
		wprintw(gd->w,"%12.6f %12.6f", gd->lat, gd->lon);
	}
	else
		wprintw(gd->w, "                  ");

//	wprintw(gd->w,"Message count: %i", gd->messagecount);

}

void gps_display_repaint(gps_display_t *gd)
{
	int width, height;
	time_t t;
	time(&t);

	color_set(COLOR_PLAIN, NULL);
	clear();

	getmaxyx(gd->w, height, width);

	color_set(COLOR_TITLE, NULL);

	wmove(gd->w, 0,0);

	wprintw(gd->w, "%-*s%*s", 20, "GPS daemon", width-19, asctime(localtime(&t)));
	color_set(COLOR_PLAIN, NULL);

	gps_display_status(gd);
	
	gps_display_location(gd);

/*
	wprintw(gd->w,"Velocity: %6.2f m/s [%6.2f north", gd->v, gd->vn);
	wmove(gd->w, 5, 0);
	wprintw(gd->w,"                      %6.2f east", gd->ve);
	wmove(gd->w, 6, 0);
	wprintw(gd->w,"                      %6.2f up   ]", gd->vu);
	wmove(gd->w, 7, 0);
	wprintw(gd->w,"Heading:  %4.2f", gd->heading);
*/

	gps_display_sats(gd);

	if (false) {
		wmove(gd->w, height-MAX_LOG_LINES-1, 0);
		hline('-', width);

		for (int i = 0; i < MAX_LOG_LINES; i++) {
			int idx = gd->logidx - i;
			if (idx < 0)
				idx += MAX_LOG_LINES;
			
			wmove(gd->w, height-1-i, 0);
			wprintw(gd->w, "%7d %s",gd->logline[idx], gd->log[idx]);
		}
	}
	
}

/** does string s2 begin with s1? **/
int strpcmp(const char *s1, const char *s2)
{
	return strncmp(s1, s2, strlen(s1));
}

void gps_log(gps_display_t *gd, const char *msg)
{
	strncpy(gd->log[gd->logidx], msg, MAX_MSG_LEN);
	gd->logline[gd->logidx]=gd->messagecount;
	gd->logidx = (gd->logidx+1)%MAX_LOG_LINES;

	refresh();
}

double pdouble(char *tok, double prev)
{
	if (tok[0]==0)
		return prev;

	return strtod(tok, NULL);
}

// returns 1 for north/east, -1 for south/west
double nsew(char a)
{
	char c = toupper(a);
	if (c=='W' || c=='S')
		return -1;
	return 1;
}

// PARSER RYAN
void gps_display_process_nmea(gps_display_t *gd, const char *_nmea)
{
	char nmea_cpy[GPS_MESSAGE_MAXLEN];
	strncpy(nmea_cpy, _nmea, GPS_MESSAGE_MAXLEN);
	char *nmea;

	//printf("PARSER_A\n");

	// get rid of any extra stuff at the beginning
	nmea = strchr(nmea_cpy, '$');
	if (nmea == NULL)
		return;

	// to-do: verify checksum

	// tokenize.
	char *toks[100];
	int  ntoks = 0;
	int  pos = 0;
	while (nmea[pos]!=0 && ntoks < 100) {
		if (nmea[pos]=='*') {
			nmea[pos]=0;
			break;
		}

		if (nmea[pos]==',') {
			nmea[pos] = 0;
			toks[ntoks++]=&nmea[pos+1];
		}
		pos++;
	}

//	gps_log(gd, nmea);
	gd->messagecount++;

	if (!strpcmp("$GPALM", nmea)) {  // almanac data
	}
	else if (!strpcmp("$GPGGA", nmea) && ntoks==14) { // GPS system fix data
		gd->utc = pdouble(toks[0], gd->utc);
		gd->lat = pdouble(toks[1], gd->lat)*nsew(toks[2][0]);
		gd->lon = pdouble(toks[3], gd->lon)*nsew(toks[4][0]);

		int qual = atoi(toks[5]);
		switch (qual)
		{
		default:
		case 0:
		case 6:
			gd->status=GPS_STATUS_NO_LOCK;
			break;
		case 1:
			gd->status=GPS_STATUS_LOCK;
			break;
		case 2:
			gd->status=GPS_STATUS_DGPS_LOCK;
			break;
		}
		gps_display_status(gd);
		gps_display_location(gd);

	}
	else if (!strpcmp("$GPGSA", nmea)) { // DOP and active satellites
	}
	else if (!strpcmp("$GPGSV", nmea) && ntoks>2) { // satellites in view
		int nsentences = atoi(toks[0]);
		int sentence = atoi(toks[1]);

		// start a new back buffer of data
		if (sentence == 1) {
			for (int i = 0; i < GPS_DISPLAY_NSATS; i++)
				gd->sat_snr_b[i] = -1;
		}

		int nsats = (ntoks-3)/4;
		for (int i = 0; i < nsats; i++) {
			int sat = atoi(toks[4*i+3]);
			if (sat < 0 || sat >= GPS_DISPLAY_NSATS)
				continue;
			gd->sat_elevation_b[sat] = strtod(toks[4*i+4], NULL);
			gd->sat_azimuth_b[sat] = strtod(toks[4*i+5], NULL);
			gd->sat_snr_b[sat] = strtod(toks[4*i+6], NULL);
		}
		
		if (sentence == nsentences) {
			for (int i = 0; i < GPS_DISPLAY_NSATS; i++) {
				gd->sat_elevation[i] = gd->sat_elevation_b[i];
				gd->sat_azimuth[i] = gd->sat_azimuth_b[i];
				gd->sat_snr[i] = gd->sat_snr_b[i];
			}
		}
		
		gps_display_sats(gd);
	}
	else if (!strpcmp("$GPRMC", nmea)) { // recommended minimum specific data
	}
	else if (!strpcmp("$GPVTG", nmea)) { // track made good and ground speed
	}
	else if (!strpcmp("$GPGLL", nmea)) { // geographic position
	}
	else if (!strpcmp("$PGRME", nmea)) { // estimated error information
		gd->err_horiz = strtod(toks[0], NULL);
		gd->err_vert = strtod(toks[2], NULL);
		gd->err_pos = strtod(toks[4], NULL);
	}
	else if (!strpcmp("$PGRMF", nmea)) { // fix data 
	}
	else if (!strpcmp("$PGRMT", nmea)) { // sensor status information (GARMIN)
	}
	else if (!strpcmp("$PGRMV", nmea) && ntoks >= 3) { // 3D velocity info

		gd->ve = pdouble(toks[0], gd->ve);
		gd->vn = pdouble(toks[1], gd->vn);
		gd->vu = pdouble(toks[2], gd->vu);

		gd->v = sqrt(gd->ve*gd->ve + gd->vn*gd->vn + gd->vu*gd->vu);
	}
	else if (!strpcmp("$PGRMB", nmea)) { // dgps beacon info
	}
	else if (!strpcmp("$PGRMM", nmea)) { // ??? e.g. "$PGRMM,WGS 84*06"
	}
	else {
		gps_log(gd, "Unknown NMEA message: ");
		gps_log(gd, nmea);
	}
	refresh();
}

/*
int main(int argc, char *argv[])
{
	FILE *f;
	int ret = 0;

	if (argc < 2)
	{
		printf("usage: %s gpslogfile\n", argv[0]);
		return -1;
	}

	gps_display_t *gd = gps_display_create();
	f = fopen(argv[1], "r");
	char buf[1024];

	gps_display_repaint(gd);

	while(fgets(buf, 1024, f)) {

		gps_process_nmea(gd, buf);

//		char c = getch();
	}

	endwin();
	return ret;
}
*/

