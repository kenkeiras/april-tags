#ifndef QRK_C_SCIP_HANDLER_H
#define QRK_C_SCIP_HANDLER_H

/*!
  \file
  \brief SCIP �R�}���h����

  \author Satofumi KAMIMURA

  $Id: scip_handler.h 783 2009-05-05 08:56:26Z satofumi $
*/

#include "urg_parameter_t.h"
#include "serial_t.h"


enum {
  ScipNoWaitReply = 0,       /*!< SCIP ������҂��Ȃ� */
  ScipWaitReply = 1,          /*!< ������҂� */

  ScipLineWidth = 64 + 1 + 1,  /*!< �P�s�̍ő咷 */
};


/*!
  \brief �R�}���h���M

  \param[out] serial �V���A������̍\����
  \param[in] send_command ���M�R�}���h

  \retval 0 ����
  \retval < 0 �G���[
*/
extern int scip_send(serial_t *serial, const char *send_command);


/*!
  \brief �R�}���h��������M����

  ret �� NULL �łȂ��ꍇ�A�R�}���h�̉����������Ɋi�[�����B�܂��Aexpected_ret �ɂ́A����Ƃ݂Ȃ��Ă悢�R�}���h�������A�I�[�� -1 �̔z��Œ�`�ł���B�R�}���h������ expected_ret �Ɋ܂܂��ꍇ�A���̊֐��̖߂�l�̓[��(����)�ƂȂ�B\n
  expected_ret �����݂���̂́A���݂̏�Ԃɐݒ肷��悤�ɃR�}���h���w�������ꍇ�ɁA�R�}���h�����Ƃ��Ă̓[���ȊO���Ԃ���Ă��܂��̂��A����Ƃ݂Ȃ��������߁B

  \param[out] serial �V���A������̍\����
  \param[out] return_code �߂�l
  \param[in] expected_ret ����Ƃ݂Ȃ��߂�l
  \param[in] timeout �^�C���A�E�g [msec]

  \retval 0 ����
  \retval < 0 �G���[
*/
extern int scip_recv(serial_t *serial, const char *command_first,
                     int* return_code, int expected_ret[],
                     int timeout);


/*!
  \brief SCIP2.0 ���[�h�ւ̑J��

  SCIP2.0 ���[�h�ɑJ�ڂ����ꍇ�A�[��(����)��Ԃ��B

  \param[in,out] serial �V���A������̍\����

  \retval 0 ����
  \retval < 0 �G���[
*/
extern int scip_scip20(serial_t *serial);


/*!
  \brief ���[�U�̏����A����̒��f

  MD ���~�߂�ړI�Ŏg����ꍇ�ɂ́A������҂��Ȃ����@�� QT ���s���s���AQT �̉����� urg_receiveData() �ŏ������邱�ƁB

  \param[in,out] serial �V���A������̍\����
  \param[in] return_code QT �R�}���h�̉���
  \param[in] wait_reply ������҂��Ȃ��Ƃ��� ScipNoWaitReply / �҂Ƃ� ScipWaitReply

  \retval 0 ����
  \retval < 0 �G���[
*/
extern int scip_qt(serial_t *serial, int *return_code, int wait_reply);


/*!
  \brief �p�����[�^���̎擾

  \param[in,out] serial �V���A������̍\����
  \param[out] parameters urg_parameter_t �\���̃����o

  \retval 0 ����
  \retval < 0 �G���[

*/
extern int scip_pp(serial_t *serial, urg_parameter_t *parameters);


/*!
  \brief �o�[�W�������̎擾

  \param[in,out] serial �V���A������̍\����
  \param[out] lines �o�[�W����������̊i�[��
  \param[in] lines_max ������̍ő吔

  \retval 0 ����
  \retval < 0 �G���[
*/
extern int scip_vv(serial_t *serial, char *lines[], int lines_max);


/*!
  \brief �{�[���[�g�̕ύX

  \param[in,out] serial �V���A������̍\����
  \param[in] baudrate �{�[���[�g

  \retval 0 ����
  \retval < 0 �G���[
*/
extern int scip_ss(serial_t *serial, long baudrate);

#endif /* !QRK_C_SCIP_HANDLER_H */
