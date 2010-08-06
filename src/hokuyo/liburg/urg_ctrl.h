#ifndef QRK_C_URG_CTRL_H
#define QRK_C_URG_CTRL_H

/*!
  \file
  \brief URG ����

  \author Satofumi KAMIMURA

  $Id: urg_ctrl.h 783 2009-05-05 08:56:26Z satofumi $

  \todo �e�֐��Ɏg�p����`����
*/

#if !defined(NO_INTENSITY)
#define USE_INTENSITY
#endif

#include "urg_t.h"


/*!
  \brief �I�v�V�����p�p�����[�^
*/
enum {
  UrgLineWidth = 64 + 1 + 1,    /*!< �P�s�̍ő咷 */
  UrgInfinityTimes = 0,         /*!< �A���f�[�^���M�w�� */
};


/*!
  \brief URG �̃R�}���h�^�C�v
*/
typedef enum {
  URG_GD,                       /*!< GD �R�}���h */
  URG_GD_INTENSITY,             /*!< GD �R�}���h(���x�f�[�^�t��) */
  URG_GS,                       /*!< GS �R�}���h */
  URG_MD,                       /*!< MD �R�}���h */
  URG_MD_INTENSITY,             /*!< MD �R�}���h(���x�f�[�^�t��) */
  URG_MS,                       /*!< MS �R�}���h */
} urg_request_type;


/*!
  \brief URG �f�[�^�͈͎w��̏ȗ��p
*/
enum {
  URG_FIRST = -1,          /*!< �S�̃f�[�^���擾����Ƃ��̊J�n�ʒu  */
  URG_LAST = -1,           /*!< �S�̃f�[�^���擾����Ƃ��̏I���ʒu  */

  UrgInvalidTimestamp = -1,     /*!< �^�C���X�^���v�̃G���[�l */
};


/*!
  \brief �ڑ�

  \param[in,out] urg URG ����̍\����
  \param[in] device �ڑ��f�o�C�X
  \param[in] baudrate �ڑ��{�[���[�g

  \retval 0 ����
  \retval <0 �G���[

  \see gd_scan.c, md_scan.c

  �g�p��
\code
urg_t urg;

// �ڑ�
if (urg_connect(&urg, "COM3", 115200) < 0) {
  printf("urg_connect: %s\n", urg_error(&urg));
  return -1;
}

...

urg_disconnect(&urg); \endcode
*/
extern int urg_connect(urg_t *urg, const char *device, long baudrate);


/*!
  \brief �ؒf

  \param[in,out] urg URG ����̍\����

  \see urg_connect()
  \see gd_scan.c, md_scan.c
*/
extern void urg_disconnect(urg_t *urg);


/*!
  \brief �ڑ�������Ԃ�

  \param[in,out] urg URG ����̍\����

  \retval 0 �ڑ���
  \retval <0 ���ڑ�

  \see urg_connect(), urg_disconnect()

  �g�p��
\code
if (urg_isConnected(&urg) < 0) {
  printf("not connected.\n");
} else {
  printf("connected.\n");
} \endcode
*/
extern int urg_isConnected(urg_t *urg);


/*!
  \brief �G���[������������̎擾

  \param[in,out] urg URG ����̍\����

  \return �G���[������������

  \see urg_connect()
  \see gd_scan.c, md_scan.c
*/
extern const char *urg_error(urg_t *urg);


/*!
  \brief �o�[�W����������̎擾

  \param[in,out] urg URG ����̍\����
  \param[out] lines �o�[�W����������̊i�[��o�b�t�@
  \param[in] lines_max �o�[�W����������i�[�̍ő�s

  \retval 0 ����
  \retval <0 �G���[

  \attention �o�b�t�@�P�s�̒����� #UrgLineWidth [byte] �ȏ�łȂ���΂Ȃ�Ȃ�

  \see get_version_lines.c
*/
extern int urg_versionLines(urg_t *urg, char* lines[], int lines_max);


/*!
  \brief URG �p�����[�^��Ԃ�

  \param[in,out] urg URG ����̍\����
  \param[out] parameters URG �p�����[�^�̍\����

  \retval 0 ����
  \retval <0 �G���[

  \see urg_maxDistance(), urg_minDistance(), urg_scanMsec(), urg_dataMax()
  \see get_parameters.c

  get_parameters.c �̎��s�� (Classic-URG)
  \verbatim
% ./get_parameters
urg_getParameters: No Error.
distance_min: 20
distance_max: 5600
area_total: 1024
area_min: 44
area_max: 725
area_front: 384
scan_rpm: 600

urg_getDistanceMax(): 5600
urg_getDistanceMin(): 20
urg_getScanMsec(): 100
urg_getDataMax(): 726 \endverbatim
  */
extern int urg_parameters(urg_t *urg, urg_parameter_t* parameters);


/*!
  \brief URG �Z���T�̃^�C�v�������Ԃ�

  \param[in,out] urg URG ����̍\����

  \retval URG �Z���T�̃^�C�v

  \code
printf("URG type: %s\n", urg_model(&urg)); \endcode
*/
extern char* urg_model(urg_t *urg);


/*!
  \brief �P�X�L�����̍ő�f�[�^����Ԃ�

  \param[in,out] urg URG ����̍\����

  \retval >=0 �P�X�L�����̍ő�f�[�^��
  \retval <0 �G���[

  \see gd_scan.c

�g�p��
\code
enum { BufferSize = 2048 };
long data[BufferSize];

...

// URG �Z���T�̎擾�f�[�^�ő�l���A��M�o�b�t�@���z���Ȃ����m�F����
// (���I�Ƀo�b�t�@�T�C�Y���擾����ꍇ�ɂ͕s�v)
int data_max = urg_dataMax(&urg);
ASSERT(BufferSize >= data_max);
\endcode
*/
extern int urg_dataMax(urg_t *urg);


/*!
  \brief �P�X�L�����̌v�����Ԃ�Ԃ�

  ���[�^���x�� 100% �w��̂Ƃ��̂P�X�L�����̌v�����Ԃ�Ԃ��B

  \param[in,out] urg URG ����̍\����

  \retval >=0 �P�X�L�����̌v������ [msec]
  \retval <0 �G���[


  \Brief 100 returns the motor speed is measured in scan time measurement time of 1% return when given one scan .
  \Param [in, out] urg URG control structures
  \retval  >=0 scan measurement time 1 0 [msec]
  \retval < 0 error

  \see urg_setMotorSpeed()

  \see md_scan.c
*/
extern int urg_scanMsec(urg_t *urg);


/*!
  \brief ����\�ȍő勗��

  \param[in,out] urg URG ����̍\����

  \retval >=0 ����\�ȍő勗�� [mm]
  \retval <0 �G���[

  \see expand_2d.c

  en:
  \brief The maximum distance measurable
  \param [in, out] urg URG control structures
  \retval> = maximum distance measurable 0 [mm]
  \retval < error 0
  \see expand_2d.c

�g�p��
\code
...
n = urg_receiveData(&urg, data, data_max);

min_distance = urg_minDistance(&urg);
max_distance = urg_minDistance(&urg);

// �L���ȃf�[�^�̂ݏo��
for (i = 0; i < n; ++i) {
  long length = data[i];
  if ((length > min_distance) && (length < max_distance)) {
    printf("%d:%d\n", i, length);
  }
}
\endcode
*/
extern long urg_maxDistance(urg_t *urg);


/*!
  \brief ����\�ȍŏ�����

  \param[in,out] urg URG ����̍\����

  \retval >=0 ����\�ȍŏ����� [mm]
  \retval <0 �G���[

  \see expand_2d.c
*/
extern long urg_minDistance(urg_t *urg);


//////////////////////////////////////////////////////////////////////


/*!
  \brief �擾�f�[�^�̃O���[�v���ݒ�

  �����̎擾�f�[�^���P�ɂ܂Ƃ߁A�擾�f�[�^�ʂ�����������B

  \param[in,out] urg URG ����̍\����
  \param[in] lines �P�ɂ܂Ƃ߂�擾�f�[�^��

  \retval 0 ����
  \retval < �G���[
*/
extern int urg_setSkipLines(urg_t *urg, int lines);


/*!
  \brief �X�L�������̊Ԉ����ݒ�

  �P��̃f�[�^�擾��A�w��X�L�����񐔂����f�[�^�擾���x�ށB

  \param[in,out] urg URG ����̍\����
  \param[in] frames �Ԉ����t���[����

  \retval 0 ����
  \retval <0 �G���[

  \attention MD/MS �R�}���h�ł̃f�[�^�擾�ɑ΂��Ă̂ݗL��

en:

 \Brief get one data set after the decimation of the number of scans , scan the specified number of times absent from the data acquisition .
 \Param [in, out] urg URG control structure
 \param [in] frames frames decimation
 \retval normal 0
 \retval < error 0 \ attention MD / MS data acquisition command only works for

*/
extern int urg_setSkipFrames(urg_t *urg, int frames);


/*!
  \brief �A���f�[�^�擾�񐔂̐ݒ�

  \param[in,out] urg URG ����̍\����
  \param[in] times �f�[�^�擾��

  \retval 0 ����
  \retval <0 �G���[

  \attention MD/MS �R�}���h�ł̃f�[�^�擾�ɑ΂��Ă̂ݗL��
  \attention 100 ��ȏ�̃f�[�^�擾���s���Ƃ��́A#UrgInfinityTimes ���w�肷�邱��

  \Brief Set the number of continuous data acquisition
  \param [in, out] urg URG control structure
  \param [in] times the number of acquisition data
  \retval normal 0
  \retval < error 0
  \attention MD / MS data acquisition command Only for
  \attention when data retrieval is over 100 , # UrgInfinityTimes to specify

  �g�p��
  \code
// ������̃f�[�^�擾
urg_setCaptureTimes(&urg, UrgInfinityTimes);

...

// ���[�U����������ƁA�f�[�^�擾�͒��~�����
urg_laserOff(&urg);
  \endcode
*/
extern int urg_setCaptureTimes(urg_t *urg, int times);


/*!
  \brief MD/MS �f�[�^�擾�ɂ�����c��X�L���������擾

  \param[in,out] urg URG ����̍\����

  \retval �c��X�L�������B�������A������̃f�[�^�擾�̂Ƃ��� 100 ��Ԃ�

  \see md_scan.c
*/
extern int urg_remainCaptureTimes(urg_t *urg);


/*!
  \brief �����f�[�^�̎擾�v��

  [first_index, last_index] �̋����f�[�^��v������Bfirst_index, last_index �ɂ��ꂼ�� URG_FIRST, URG_LAST ���w�肷�邱�ƂŁA�S�͈͂̃f�[�^�擾���s�킹�邱�Ƃ��ł���B

  \param[in,out] urg URG ����̍\����
  \param[in] request_type �f�[�^�擾�^�C�v
  \param[in] first_index �f�[�^�擾�J�n�C���f�b�N�X
  \param[in] last_index �f�[�^�擾�I���C���f�b�N�X

  \retval 0 ����
  \retval <0 �G���[

  \see urg_receiveData()
  \see gd_scan.c, md_scan.c

�g�p��
\code
// GD �X�L�����ɂ��P�X�L�������̃f�[�^�擾
urg_requestData(&urg, URG_GD, URG_FIRST, URG_LAST);
n = urg_receiveData(&urg, data, data_max);

// MD �X�L�����ɂ��A���̃f�[�^�擾
urg_requestData(&urg, URG_MD, URG_FIRST, URG_LAST);
while (1) {
  n = urg_receiveData(&urg, data, data_max);
  if (n > 0) {
    // �f�[�^�̕\���Ȃ�
    ...
  }
}
\endcode
*/
extern int urg_requestData(urg_t *urg,
                           urg_request_type request_type,
                           int first_index,
                           int last_index);


/*!
  \brief URG �f�[�^�̎�M

  \param[in,out] urg URG ����̍\����
  \param[out] data ��M�f�[�^�̊i�[��
  \param[in] data_max ��M�f�[�^�̍ő吔

  \retval 0 > �擾�f�[�^��
  \retval <0 �G���[

  \see urg_requestData()
*/
extern int urg_receiveData(urg_t *urg, long data[], int data_max);


#if defined(USE_INTENSITY)
/*!
  \brief ���x�t���f�[�^�̎擾

  \param[in,out] urg URG ����̍\����
  \param[out] data ��M�f�[�^�̊i�[��
  \param[in] data_max ��M�f�[�^�̍ő吔
  \param[out] intensity ���x�̎�M�f�[�^�i�[��

  \attention Classic-URG, Top-URG �̂ݑΉ� (2009-04-19 ����)
  \attention Top-URG �� ME �Ή��́A�擾�f�[�^�̃O���[�s���O�����Q�ɌŒ�
*/
extern int urg_receiveDataWithIntensity(urg_t *urg, long data[], int data_max,
                                        long intensity[]);
#endif


/*!
  \brief URG �f�[�^�̕�����M

  \param[in,out] urg URG ����̍\����
  \param[out] data ��M�f�[�^�̊i�[��
  \param[in] data_max ��M�f�[�^�̍ő吔
  \param[in] first_index �f�[�^�i�[�J�n�C���f�b�N�X
  \param[in] last_index �f�[�^�i�[�I���C���f�b�N�X

  \retval 0 > �擾�f�[�^��
  \retval <0 �G���[

  \attention ������

  \see gd_scan.c, md_scan.c
*/
extern int urg_receivePartialData(urg_t *urg, long data[], int data_max,
                                  int first_index, int last_index);


/*!
  \brief �^�C���X�^���v�̎擾

  \param[in,out] urg URG ����̍\����

  \retval �^�C���X�^���v [msec]

  \see md_scan.c


en:
 \Brief Get the timestamp
 \param [in, out] urg URG control structures
 \retval timestamp [msec]
 \see md_scan.c

�g�p��
\code
urg_requestData(&urg, URG_GD, URG_FIRST, URG_LAST);
n = urg_receiveData(&urg, data, data_max);
if (n > 0) {
  long timestamp = urg_recentTimestamp(&urg);
  printf("timestamp: %d\n", timestamp);

  // �f�[�^�̕\���Ȃ�
  // !!!
}
\endcode
*/
extern long urg_recentTimestamp(urg_t *urg);


//////////////////////////////////////////////////////////////////////


/*!
  \brief �C���f�b�N�X�l�� radian �p�x�ɕϊ�

  \image html urg_sensor_radian.png �Z���T���ʂ� X ���̐��̕���

  \param[in,out] urg URG ����̍\����
  \param[in] index �C���f�b�N�X�l

  \return �p�x [radian]

  \see index_convert.c
*/
extern double urg_index2rad(urg_t *urg, int index);


/*!
  \brief �C���f�b�N�X�l�� degree �p�x�ɕϊ�

  \param[in,out] urg URG ����̍\����
  \param[in] index �C���f�b�N�X�l

  \return �p�x [degree]

  \see index_convert.c
*/
extern int urg_index2deg(urg_t *urg, int index);


/*!
  \brief radian �p�x���C���f�b�N�X�l�ɕϊ�

  \image html urg_sensor_radian.png �Z���T���ʂ� X ���̐��̕���

  \param[in,out] urg URG ����̍\����
  \param[in] radian �p�x

  \return �C���f�b�N�X�l

  \see index_convert.c
*/
extern int urg_rad2index(urg_t *urg, double radian);


/*!
  \brief degree �p�x���C���f�b�N�X�l�ɕϊ�

  \param[in,out] urg URG ����̍\����
  \param[in] degree �p�x

  \return �C���f�b�N�X�l

  \see index_convert.c
*/
extern int urg_deg2index(urg_t *urg, int degree);


//////////////////////////////////////////////////////////////////////


/*!
  \brief ���[�U�̓_���w��

  \param[in,out] urg URG ����̍\����

  \retval 0 ����
  \retval <0 �G���[

  \see gd_scan.c
*/
extern int urg_laserOn(urg_t *urg);


/*!
  \brief ���[�U�̏����w��

  \param[in,out] urg URG ����̍\����

  \retval 0 ����
  \retval <0 �G���[
*/
extern int urg_laserOff(urg_t *urg);


//////////////////////////////////////////////////////////////////////


/*!
  \brief �^�C���X�^���v���[�h�ɓ���

  \param[in,out] urg URG ����̍\����

  \retval 0 ����
  \retval <0 �G���[
*/
extern int urg_enableTimestampMode(urg_t *urg);


/*!
  \brief �^�C���X�^���v���[�h���甲����

  \param[in,out] urg URG ����̍\����

  \retval 0 ����
  \retval <0 �G���[
*/
extern int urg_disableTimestampMode(urg_t *urg);


/*!
  \brief �^�C���X�^���v�̎擾

  TM1 �̉�����Ԃ��B

  \param[in,out] urg URG ����̍\����

  \retval >=0 �^�C���X�^���v [msec]
  \retval <0 �G���[

�g�p��
\code
// �^�C���X�^���v���[�h�ɓ���
urg_enableTimestampMode(&urg);

// URG �̃^�C���X�^���v��A�����Ď擾
for (i = 0; i < 5; ++i) {
  long timestamp = urg_currentTimestamp(&urg);
  printf("timestamp: %ld\n", timestamp)
}

// �^�C���X�^���v���[�h���甲����
urg_disableTimestampMode(&urg);
\endcode
*/
extern long urg_currentTimestamp(urg_t *urg);

#endif /* !QRK_C_URG_CTRL_H */
