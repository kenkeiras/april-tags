/*!
  \file
  \brief URG �̃G���[�R�[�h

  \author Satofumi KAMIMURA

  $Id: urg_errno.c 777 2009-05-05 08:12:10Z satofumi $
*/

#include "urg_errno.h"


/* �G���[�������Ԃ� */
const char* urg_strerror(int errno)
{
  /* !!! �ǉ��A�ύX�Ɏシ���B�C�����ׂ� */

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
    /* �G���[�����ȊO�̏ꍇ�́A���툵���ɂ��� */
    errno = 0;
  }

  return errorStr[-errno];
}
