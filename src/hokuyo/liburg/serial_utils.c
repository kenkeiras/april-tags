/*!
  \file
  \brief �V���A������M�̕⏕

  \author Satofumi KAMIMURA

  $Id: serial_utils.c 882 2009-05-13 19:30:41Z satofumi $
*/

#include "serial_utils.h"
#include "serial_ctrl.h"

#include <stdio.h>
#include <ctype.h>


/* ���s���̔��� */
int serial_isLF(const char ch)
{
  return ((ch == '\r') || (ch == '\n')) ? 1 : 0;
}


/* ��M�f�[�^�̓ǂݔ�΂� */
void serial_skip(serial_t *serial, int total_timeout, int each_timeout)
{
  char recv_ch;

  /* �����߂����������N���A */
  serial->last_ch_ = '\0';

  if (each_timeout <= 0) {
    each_timeout = total_timeout;
  }

  // !!! total_timeout �����̃��[�v�����ɓK�p���ׂ�
  while (1) {
    int n = serial_recv(serial, &recv_ch, 1, each_timeout);
    if (n <= 0) {
      break;
    }
  }
}


/* ���s�܂ł̓ǂ݂��� */
int serial_getLine(serial_t *serial, char* data, int data_size_max,
                   int timeout)
{
  /* �P�������ǂ݂����ĕ]������ */
  int filled = 0;
  int is_timeout = 0;

  while (filled < data_size_max) {
    char recv_ch;
    int n = serial_recv(serial, &recv_ch, 1, timeout);
    if ((n <= 0) || serial_isLF(recv_ch)) {
      is_timeout = 1;
      break;
    }
    data[filled++] = recv_ch;
  }
  if (filled == data_size_max) {
    --filled;
    serial_ungetc(serial, data[filled]);
  }
  data[filled] = '\0';

  if ((filled == 0) && is_timeout) {
    return -1;
  } else {
    return filled;
  }
}
