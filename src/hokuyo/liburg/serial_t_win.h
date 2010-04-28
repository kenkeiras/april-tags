#ifndef QRK_C_SERIAL_T_WIN_H
#define QRK_C_SERIAL_T_WIN_H

/*!
  \file
  \brief �V���A������̍\���� (Windows ����)

  \author Satofumi KAMIMURA

  $Id: serial_t_win.h 881 2009-05-13 18:25:04Z satofumi $
*/

#include <windows.h>


enum {
  SerialErrorStringSize = 256,
};


/*!
  \brief �V���A������̍\����
*/
typedef struct {

  HANDLE hCom_;                 /*!< �ڑ����\�[�X */
  char last_ch_;                /*!< �����߂����P���� */
  int current_timeout_;		/*!< ���݂̃^�C���A�E�g�ݒ� */
  HANDLE hEvent_;
  OVERLAPPED overlapped_;

} serial_t;

#endif /* !QRK_C_SERIAL_T_LIN_H */
