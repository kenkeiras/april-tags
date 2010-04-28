#ifndef QRK_C_SERIAL_T_LIN_H
#define QRK_C_SERIAL_T_LIN_H

/*!
  \file
  \brief �V���A������̍\���� (Linux, Mac ����)

  \author Satofumi KAMIMURA

  $Id: serial_t_lin.h 783 2009-05-05 08:56:26Z satofumi $
*/

#include <termios.h>


enum {
  SerialErrorStringSize = 256,
};

#define SERIAL_BUF_CAPACITY 16384


/*!
  \brief �V���A������̍\����
*/
typedef struct {

  int errno_;                                //!< �G���[�ԍ�
  char error_string_[SerialErrorStringSize]; //!< �G���[������
  int fd_;                                   //!< �ڑ����\�[�X
  struct termios sio_;                       //!< �^�[�~�i������
  char last_ch_;                             //!< �����߂����P����

  char buf[SERIAL_BUF_CAPACITY];
  int buf_capacity;
  int buf_head;
  int buf_filled;
} serial_t;

#endif /*! QRK_C_SERIAL_T_LIN_H */
