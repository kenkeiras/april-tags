#ifndef QRK_C_SCIP_HANDLER_H
#define QRK_C_SCIP_HANDLER_H

/*!
  \file
  \brief SCIP コマンド処理

  \author Satofumi KAMIMURA

  $Id: scip_handler.h 783 2009-05-05 08:56:26Z satofumi $
*/

#include "urg_parameter_t.h"
#include "serial_t.h"


enum {
  ScipNoWaitReply = 0,       /*!< SCIP 応答を待たない */
  ScipWaitReply = 1,          /*!< 応答を待つ */

  ScipLineWidth = 64 + 1 + 1,  /*!< １行の最大長 */
};


/*!
  \brief コマンド送信

  \param[out] serial シリアル制御の構造体
  \param[in] send_command 送信コマンド

  \retval 0 正常
  \retval < 0 エラー
*/
extern int scip_send(serial_t *serial, const char *send_command);


/*!
  \brief コマンド応答を受信する

  ret が NULL でない場合、コマンドの応答がここに格納される。また、expected_ret には、正常とみなしてよいコマンド応答を、終端が -1 の配列で定義できる。コマンド応答が expected_ret に含まれる場合、この関数の戻り値はゼロ(正常)となる。\n
  expected_ret が存在するのは、現在の状態に設定するようにコマンドを指示した場合に、コマンド応答としてはゼロ以外が返されてしまうのを、正常とみなしたいため。

  \param[out] serial シリアル制御の構造体
  \param[out] return_code 戻り値
  \param[in] expected_ret 正常とみなす戻り値
  \param[in] timeout タイムアウト [msec]

  \retval 0 正常
  \retval < 0 エラー
*/
extern int scip_recv(serial_t *serial, const char *command_first,
                     int* return_code, int expected_ret[],
                     int timeout);


/*!
  \brief SCIP2.0 モードへの遷移

  SCIP2.0 モードに遷移した場合、ゼロ(正常)を返す。

  \param[in,out] serial シリアル制御の構造体

  \retval 0 正常
  \retval < 0 エラー
*/
extern int scip_scip20(serial_t *serial);


/*!
  \brief レーザの消灯、測定の中断

  MD を止める目的で使われる場合には、応答を待たない方法で QT 発行を行い、QT の応答は urg_receiveData() で処理すること。

  \param[in,out] serial シリアル制御の構造体
  \param[in] return_code QT コマンドの応答
  \param[in] wait_reply 応答を待たないときに ScipNoWaitReply / 待つとき ScipWaitReply

  \retval 0 正常
  \retval < 0 エラー
*/
extern int scip_qt(serial_t *serial, int *return_code, int wait_reply);


/*!
  \brief パラメータ情報の取得

  \param[in,out] serial シリアル制御の構造体
  \param[out] parameters urg_parameter_t 構造体メンバ

  \retval 0 正常
  \retval < 0 エラー

*/
extern int scip_pp(serial_t *serial, urg_parameter_t *parameters);


/*!
  \brief バージョン情報の取得

  \param[in,out] serial シリアル制御の構造体
  \param[out] lines バージョン文字列の格納先
  \param[in] lines_max 文字列の最大数

  \retval 0 正常
  \retval < 0 エラー
*/
extern int scip_vv(serial_t *serial, char *lines[], int lines_max);


/*!
  \brief ボーレートの変更

  \param[in,out] serial シリアル制御の構造体
  \param[in] baudrate ボーレート

  \retval 0 正常
  \retval < 0 エラー
*/
extern int scip_ss(serial_t *serial, long baudrate);

#endif /* !QRK_C_SCIP_HANDLER_H */
