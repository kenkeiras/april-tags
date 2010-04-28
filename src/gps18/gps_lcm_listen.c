// file: gps_lcm_listen.c
// desc: listens for nmea messages transmitted via LC, and displays some basic
//       GPS information as it arrives over the network.

#include <stdio.h>
#include <string.h>
#include <pthread.h>

#include <lcmtypes/nmea_t.h>
//#include <common/nmea.h>		//RYAN

#include "gps_display.h"

typedef struct _state {
    struct gps_display *gd;
    lcm_t *lcm;
    pthread_t gps_tid;
} state_t;

int on_nmea( const char *channel, const nmea_t *_nmea, state_t *s )
{
    gps_display_process_nmea( s->gd, _nmea->nmea );
    return 0;
}

int main(int argc, char **argv)
{
    state_t s;


    fprintf(stderr, "HERE_M_%c\n", 'a');
    memset( &s, 0, sizeof(s) );

    fprintf(stderr, "HERE_M_%c\n", 'b');
    s.gd = gps_display_create();
    fprintf(stderr, "HERE_M_%c\n", 'c');
    return 0;

    // initialize LC
    if ((s.lcm = lcm_create(NULL)) == NULL)
        fprintf(stderr, "error initializing LC!\n"); return 1;
    /*  RYAN replaced below for above
    s.lc = lcm_create();
    if( NULL == s.lc ) { fprintf(stderr, "error allocating LC.  "); return 1; }
    if( 0 != lcm_init(s.lc, NULL)) {
        fprintf(stderr, "error initializing LC!\n"); return 1; }
	*/

    // subscribe to GPS NMEA messages
    lcm_subscribe( s.lcm, "NMEA", (lcm_msg_handler_t) on_nmea, &s );
    //nmea_t_lcm_subscribe( s.lc, "NMEA", (nmea_t_lcm_handler_t) on_nmea, &s );
    
    fprintf(stderr, "subscribed\n");

    s.gps_tid = gps_display_start(s.gd);
 
    while(1) {
        lcm_handle( s.lcm );
    }

    lcm_destroy( s.lcm );

    // TODO cleanup gps_display

    return 0;
}
