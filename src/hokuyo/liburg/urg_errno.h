#ifndef QRK_C_URG_ERRNO_H
#define QRK_C_URG_ERRNO_H

/*!
  \file
  \brief URG �̃G���[�R�[�h

  \author Satofumi KAMIMURA

  $Id: urg_errno.h 783 2009-05-05 08:56:26Z satofumi $
*/


enum {
  UrgNoError = 0,               /*!< ���� */
  UrgNotImplemented = -1,       /*!< ������ */
  UrgSendFail = -2,
  UrgRecvFail = -3,
  UrgScip10 = -4,               /*!< SCIP1.0 ���� */
  UrgSsFail = -5,               /*!< SS �R�}���h�����Ɏ��s */
  UrgAdjustBaudrateFail = -6,   /*!< �{�[���[�g���킹�Ɏ��s */
  UrgInvalidArgs = -7,          /*!< �s���Ȉ����w�� */
  UrgInvalidResponse = -8,      /*!< URG ���̉����G���[ */
  UrgSerialConnectionFail = -9, /*!< �V���A���ڑ��Ɏ��s */
  UrgSerialRecvFail = -10,      /*!< �V���A���ڑ��Ɏ��s */
  UrgMismatchResponse = -11,    /*!< �G�R�[�o�b�N�������قȂ� */
  UrgNoResponse = -12,          /*!< �����Ȃ� */
  UtmNoGDIntensity = -13, /*!< UTM-30LX �� GD �ŋ��x�f�[�^�͎擾�ł��Ȃ� */
};


/*!
  \brief �G���[�������������Ԃ�

  \param[in] urg_errno URG �̃G���[�߂�l

  \return �G���[������������
*/
extern const char* urg_strerror(int urg_errno);

#endif /* !QRK_C_URG_ERRNO_H */
