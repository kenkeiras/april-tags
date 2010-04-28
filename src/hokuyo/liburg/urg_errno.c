/*!
  \file
  \brief URG のエラーコード

  \author Satofumi KAMIMURA

  $Id: urg_errno.c 777 2009-05-05 08:12:10Z satofumi $
*/

#include "urg_errno.h"


/* エラー文字列を返す */
const char* urg_strerror(int errno)
{
  /* !!! 追加、変更に弱すぎ。修正すべき */

  const char *errorStr[] = {
    "No Error.",
    "Not Implemented.",
    "Send fail.",
    "Receive fail.",
    "SCIP1.1 protocol is not supported. Please update URG firmware.",
    "SS fail.",
    "Adjust baudrate fail.",
    "Invalid parameters.",
    "Urg invalid response.",
    "Serial connection fail.",
    "Serial receive fail.",
    "Response mismatch.",
    "No Response.",
    "UTM-30LX is not supported GD_INTENSITY. Please use MD_INTENSITY",
    "dummy.",

    /* !!! */
  };

  if (errno > 0) {
    /* エラー応答以外の場合は、正常扱いにする */
    errno = 0;
  }

  return errorStr[-errno];
}
