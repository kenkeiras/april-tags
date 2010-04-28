/*!
  \file
  \brief タイムスタンプ取得関数

  \author Satofumi KAMIMURA

  $Id: getTicks.c 779 2009-05-05 08:15:13Z satofumi $
*/

#include "getTicks.h"
#include "detect_os.h"
#if defined WINDOWS_OS
#include <time.h>
#else
#include <sys/time.h>
#include <stdio.h>
#endif


int getTicks(void)
{
  int ticks = 0;

#if defined LINUX_OS
  // Linux で SDL がない場合の実装。最初の呼び出しは 0 を返す
  static int first_ticks = 0;
  struct timeval tvp;
  gettimeofday(&tvp, NULL);
  int global_ticks = tvp.tv_sec * 1000 + tvp.tv_usec / 1000;
  if (first_ticks == 0) {
    first_ticks = global_ticks;
  }
  ticks = global_ticks - first_ticks;

#else
  ticks = (int)(clock() / (CLOCKS_PER_SEC / 1000.0));
#endif
  return ticks;
}
