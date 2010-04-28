/*!
  \file
  \brief SCIP �R�}���h����

  \author Satofumi KAMIMURA

  $Id: scip_handler.c 882 2009-05-13 19:30:41Z satofumi $

  \todo �擾�s�̃`�F�b�N�T���𔻒�
  \todo �o�[�W�������̍s�𔻕ʂ��邽�߂ɁA������t������
*/

#include "scip_handler.h"
#include "serial_errno.h"
#include "serial_ctrl.h"
#include "serial_utils.h"
#include "urg_errno.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>

#if defined(WINDOWS_OS)
#define snprintf _snprintf
#endif

/*! \todo urg_ctrl.c �Ƌ��ʂɂ��ׂ� */
enum {
  ScipTimeout = 1000,           /*!< [msec] */
  EachTimeout = 100,		/*!< [msec] */
};


/* �R�}���h���M */
int scip_send(serial_t *serial, const char *send_command)
{
  int n = (int)strlen(send_command);
  return serial_send(serial, send_command, n);
}


/*!
  \brief �R�}���h�����̎�M

  \todo �`�F�b�N�T���̃e�X�g���s��
*/
int scip_recv(serial_t *serial, const char *command_first,
              int* return_code, int expected_ret[], int timeout)
{
  char recv_ch = '\0';
  int ret_code = 0;
  int n;
  int i;

  /* �����̎�M */
  char buffer[ScipLineWidth];

  /* �ŏ��̉�����ǂݔ�΂� */
  n = serial_getLine(serial, buffer, ScipLineWidth, timeout);
  if (n < 0) {
    return UrgSerialRecvFail;
  }

  // �V���A���ڑ��Ń{�[���[�g�ύX����� 0x00 �́A����O�Ƃ���
  if (! ((n == 1) && (buffer[0] == 0x00))) {
    if (strncmp(buffer, command_first, 2)) {
      /* ���M�����ƈ�v���Ȃ���΁A�G���[�����ɂ��� */
      return UrgMismatchResponse;
    }
  }

  /* ����������̓ǂ݂����ƃp�[�X */
  n = serial_getLine(serial, buffer, ScipLineWidth, timeout);

  /* �Ō�̂P���������s�ȊO�Ȃ�΁A�ێ����Ď���̓ǂݏo���Ŏg�� */
  n = serial_recv(serial, &recv_ch, 1, timeout);
  if ((n == 1) && (! serial_isLF(recv_ch))) {
    serial_ungetc(serial, recv_ch);
  }

  /* ��M���������������\���������̂Ȃ�΁A0 ��Ԃ� */
  ret_code = strtol(buffer, NULL, 16);
  if (return_code != NULL) {
    *return_code = ret_code;
  }
  for (i = 0; expected_ret[i] != -1; ++i) {
    if (ret_code == expected_ret[i]) {
      return 0;
    }
  }
  return ret_code;
}


/* SCIP2.0 �ւ̑J�� */
int scip_scip20(serial_t *serial)
{
  int expected_ret[] = { 0x0, 0xE, -1 };
  int ret;

  ret = scip_send(serial, "SCIP2.0\r");
  if (ret != 8) {
    return ret;
  }

  return scip_recv(serial, "SC", NULL, expected_ret, ScipTimeout);
}


/* QT �R�}���h�̑��M */
int scip_qt(serial_t *serial, int *return_code, int wait_reply)
{
  int expected_ret[] = { 0x0, -1 };
  int ret;

  ret = scip_send(serial, "QT\r");
  if (ret != 3) {
    return ret;
  }

  if (wait_reply == ScipNoWaitReply) {
    return 0;
  }

  ret = scip_recv(serial, "QT", return_code, expected_ret, ScipTimeout);
  if (return_code && (*return_code == 0xE)) {
    *return_code = -(*return_code);
    return UrgScip10;
  }

  return ret;
}


/* PP ���̎擾 */
int scip_pp(serial_t *serial, urg_parameter_t *parameters)
{
  int send_n;
  int ret = 0;
  int expected_reply[] = { 0x0, -1 };
  int n;
  int i;

  char buffer[ScipLineWidth];

  /* PP �R�}���h�̑��M */
  send_n = scip_send(serial, "PP\r");
  if (send_n != 3) {
    /* !!! urg->errno = UrgSendFail; */
    return SerialSendFail;
  }

  /* �����̎�M */
  ret = scip_recv(serial, "PP", NULL, expected_reply, ScipTimeout);
  if (ret < 0) {
    /* urg->errno = UrgRecvFail; */
    return ret;
  }

  /* �p�����[�^������̎�M */
  for (i = 0; i < UrgParameterLines; ++i) {
    n = serial_getLine(serial, buffer, ScipLineWidth, ScipTimeout);
    if (n <= 0) {
      return ret;
    }

    /* !!! AMIN �Ȃǂ̕�����Ɣ��菈�������ׂ� */

    if (i == 0) {
      strncpy(parameters->sensor_type,  &buffer[5], 8);
      parameters->sensor_type[8] = '\0';

    } else if (i == 1) {
      parameters->distance_min_ = atoi(&buffer[5]);

    } else if (i == 2) {
      parameters->distance_max_ = atoi(&buffer[5]);

    } else if (i == 3) {
      parameters->area_total_ = atoi(&buffer[5]);

    } else if (i == 4) {
      parameters->area_min_ = atoi(&buffer[5]);

    } else if (i == 5) {
      parameters->area_max_ = atoi(&buffer[5]);

    } else if (i == 6) {
      parameters->area_front_ = atoi(&buffer[5]);

    } else if (i == 7) {
      parameters->scan_rpm_ = atoi(&buffer[5]);
    }
  }

  return 0;
}


/* VV �����̎�M */
int scip_vv(serial_t *serial, char *lines[], int lines_max)
{
  int send_n;
  int ret = 0;
  int expected_reply[] = { 0x0, -1 };
  int n;
  int i;

  /* �󃁃b�Z�[�W�ŏ����� */
  for (i = 0; i < lines_max; ++i) {
    *lines[i] = '\0';
  }

  /* VV �R�}���h�̑��M */
  send_n = scip_send(serial, "VV\r");
  if (send_n != 3) {
    /* !!! urg->errno = UrgSendFail; */
    return SerialSendFail;
  }

  /* �����̎�M */
  ret = scip_recv(serial, "VV", NULL, expected_reply, ScipTimeout);
  if (ret < 0) {
    /* urg->errno = UrgRecvFail; */
    return ret;
  }

  /* �o�[�W����������̎�M */
  for (i = 0; i < lines_max; ++i) {
    n = serial_getLine(serial, lines[i], ScipLineWidth, ScipTimeout);
    if (n <= 0) {
      return ret;
    }
  }

  serial_skip(serial, ScipTimeout, EachTimeout);
  return ret;
}


/* SS �ɂ��{�[���[�g�̕ύX */
int scip_ss(serial_t *serial, long baudrate)
{
  int expected_reply[] = { 0x0, 0x3, 0x4, -1 };
  int send_n;
  int ret;

  /* !!! �K�肳�ꂽ�{�[���[�g�ȊO�̓G���[�����ɂ��ׂ� */

  /* SS �R�}���h�̑��M */
  char buffer[] = "SSxxxxxx\r";
  snprintf(buffer, 10, "SS%06ld\r", baudrate);
  send_n = scip_send(serial, buffer);
  if (send_n != 9) {
    /* !!! urg->errno = UrgSendFail; */
    return SerialSendFail;
  }

  /* �����̎�M */
  ret = scip_recv(serial, "SS", NULL, expected_reply, ScipTimeout);
  if (ret < 0) {
    /* urg->errno = UrgRecvFail; */
    return ret;
  }

  return 0;
}
