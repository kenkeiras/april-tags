#ifndef QRK_C_SERIAL_T_LIN_H
#define QRK_C_SERIAL_T_LIN_H

/*!
  \file
  \brief シリアル制御の構造体 (Linux, Mac 実装)

  \author Satofumi KAMIMURA

  $Id: serial_t_lin.h 783 2009-05-05 08:56:26Z satofumi $
*/

#include <termios.h>


enum {
  SerialErrorStringSize = 256,
};

#define SERIAL_BUF_CAPACITY 16384


/*!
  \brief シリアル制御の構造体
*/
typedef struct {

  int errno_;                                //!< エラー番号
  char error_string_[SerialErrorStringSize]; //!< エラー文字列
  int fd_;                                   //!< 接続リソース
  struct termios sio_;                       //!< ターミナル制御
  char last_ch_;                             //!< 書き戻した１文字

  char buf[SERIAL_BUF_CAPACITY];
  int buf_capacity;
  int buf_head;
  int buf_filled;
} serial_t;

#endif /*! QRK_C_SERIAL_T_LIN_H */
