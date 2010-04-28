/*!
  \file
  \brief ë“ã@ä÷êî

  \author Satofumi KAMIMURA

  $Id: delay.c 779 2009-05-05 08:15:13Z satofumi $
*/

#include "detect_os.h"
#if defined(WINDOWS_OS)
#include <windows.h>
#include <time.h>
#else
#include <unistd.h>
#include <sys/time.h>
#endif


void delay(int msec) {

#if defined(WINDOWS_OS)
  Sleep(msec);

#else
  // !!! Mac Ç≈ìÆçÏÇµÇ»Ç¢ÇÊÇ§Ç»ÇÁÇŒÅAí≤êÆÇ∑ÇÈ
  usleep(1000 * msec);
#endif
}
