/*!
  \file
  \brief �ҋ@�֐�

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
  // !!! Mac �œ��삵�Ȃ��悤�Ȃ�΁A��������
  usleep(1000 * msec);
#endif
}
