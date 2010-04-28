/*!
  \file
  \brief SCIP コマンド処理

  \author Satofumi KAMIMURA

  $Id: scip_handler.c 882 2009-05-13 19:30:41Z satofumi $

  \todo 取得行のチェックサムを判定
  \todo バージョン情報の行を判別するために、引数を付加する
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

/*! \todo urg_ctrl.c と共通にすべき */
enum {
  ScipTimeout = 1000,           /*!< [msec] */
  EachTimeout = 100,		/*!< [msec] */
};


/* コマンド送信 */
int scip_send(serial_t *serial, const char *send_command)
{
  int n = (int)strlen(send_command);
  return serial_send(serial, send_command, n);
}


/*!
  \brief コマンド応答の受信

  \todo チェックサムのテストを行う
*/
int scip_recv(serial_t *serial, const char *command_first,
              int* return_code, int expected_ret[], int timeout)
{
  char recv_ch = '\0';
  int ret_code = 0;
  int n;
  int i;

  /* 応答の受信 */
  char buffer[ScipLineWidth];

  /* 最初の応答を読み飛ばす */
  n = serial_getLine(serial, buffer, ScipLineWidth, timeout);
  if (n < 0) {
    return UrgSerialRecvFail;
  }

  // シリアル接続でボーレート変更直後の 0x00 は、判定外とする
  if (! ((n == 1) && (buffer[0] == 0x00))) {
    if (strncmp(buffer, command_first, 2)) {
      /* 送信文字と一致しなければ、エラー扱いにする */
      return UrgMismatchResponse;
    }
  }

  /* 応答文字列の読みだしとパース */
  n = serial_getLine(serial, buffer, ScipLineWidth, timeout);

  /* 最後の１文字が改行以外ならば、保持して次回の読み出しで使う */
  n = serial_recv(serial, &recv_ch, 1, timeout);
  if ((n == 1) && (! serial_isLF(recv_ch))) {
    serial_ungetc(serial, recv_ch);
  }

  /* 受信した応答文字が予期されるものならば、0 を返す */
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


/* SCIP2.0 への遷移 */
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


/* QT コマンドの送信 */
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


/* PP 情報の取得 */
int scip_pp(serial_t *serial, urg_parameter_t *parameters)
{
  int send_n;
  int ret = 0;
  int expected_reply[] = { 0x0, -1 };
  int n;
  int i;

  char buffer[ScipLineWidth];

  /* PP コマンドの送信 */
  send_n = scip_send(serial, "PP\r");
  if (send_n != 3) {
    /* !!! urg->errno = UrgSendFail; */
    return SerialSendFail;
  }

  /* 応答の受信 */
  ret = scip_recv(serial, "PP", NULL, expected_reply, ScipTimeout);
  if (ret < 0) {
    /* urg->errno = UrgRecvFail; */
    return ret;
  }

  /* パラメータ文字列の受信 */
  for (i = 0; i < UrgParameterLines; ++i) {
    n = serial_getLine(serial, buffer, ScipLineWidth, ScipTimeout);
    if (n <= 0) {
      return ret;
    }

    /* !!! AMIN などの文字列と判定処理をすべき */

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


/* VV 応答の受信 */
int scip_vv(serial_t *serial, char *lines[], int lines_max)
{
  int send_n;
  int ret = 0;
  int expected_reply[] = { 0x0, -1 };
  int n;
  int i;

  /* 空メッセージで初期化 */
  for (i = 0; i < lines_max; ++i) {
    *lines[i] = '\0';
  }

  /* VV コマンドの送信 */
  send_n = scip_send(serial, "VV\r");
  if (send_n != 3) {
    /* !!! urg->errno = UrgSendFail; */
    return SerialSendFail;
  }

  /* 応答の受信 */
  ret = scip_recv(serial, "VV", NULL, expected_reply, ScipTimeout);
  if (ret < 0) {
    /* urg->errno = UrgRecvFail; */
    return ret;
  }

  /* バージョン文字列の受信 */
  for (i = 0; i < lines_max; ++i) {
    n = serial_getLine(serial, lines[i], ScipLineWidth, ScipTimeout);
    if (n <= 0) {
      return ret;
    }
  }

  serial_skip(serial, ScipTimeout, EachTimeout);
  return ret;
}


/* SS によるボーレートの変更 */
int scip_ss(serial_t *serial, long baudrate)
{
  int expected_reply[] = { 0x0, 0x3, 0x4, -1 };
  int send_n;
  int ret;

  /* !!! 規定されたボーレート以外はエラー扱いにすべき */

  /* SS コマンドの送信 */
  char buffer[] = "SSxxxxxx\r";
  snprintf(buffer, 10, "SS%06ld\r", baudrate);
  send_n = scip_send(serial, buffer);
  if (send_n != 9) {
    /* !!! urg->errno = UrgSendFail; */
    return SerialSendFail;
  }

  /* 応答の受信 */
  ret = scip_recv(serial, "SS", NULL, expected_reply, ScipTimeout);
  if (ret < 0) {
    /* urg->errno = UrgRecvFail; */
    return ret;
  }

  return 0;
}
