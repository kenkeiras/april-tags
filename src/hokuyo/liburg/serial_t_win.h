#ifndef QRK_C_SERIAL_T_WIN_H
#define QRK_C_SERIAL_T_WIN_H

/*!
  \file
  \brief シリアル制御の構造体 (Windows 実装)

  \author Satofumi KAMIMURA

  $Id: serial_t_win.h 881 2009-05-13 18:25:04Z satofumi $
*/

#include <windows.h>


enum {
  SerialErrorStringSize = 256,
};


/*!
  \brief シリアル制御の構造体
*/
typedef struct {

  HANDLE hCom_;                 /*!< 接続リソース */
  char last_ch_;                /*!< 書き戻した１文字 */
  int current_timeout_;		/*!< 現在のタイムアウト設定 */
  HANDLE hEvent_;
  OVERLAPPED overlapped_;

} serial_t;

#endif /* !QRK_C_SERIAL_T_LIN_H */
