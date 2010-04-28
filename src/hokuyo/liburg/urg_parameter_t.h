#ifndef QRK_URG_PARAMETER_T_H
#define QRK_URG_PARAMETER_T_H

/*!
  \file
  \brief URG �̃p�����[�^���

  \author Satofumi KAMIMURA

  $Id: urg_parameter_t.h 783 2009-05-05 08:56:26Z satofumi $
*/


enum {
  UrgParameterLines = 8 + 1 + 1,
};


/*!
  \brief URG �̃p�����[�^���
*/
typedef struct {
  char sensor_type[80];         /*!< �Z���T�^�C�v */
  long distance_min_;		/*!< DMIN ��� */
  long distance_max_;		/*!< DMAX ��� */
  int area_total_;		/*!< ARES ��� */
  int area_min_;		/*!< AMIN ��� */
  int area_max_;		/*!< AMAX ��� */
  int area_front_;		/*!< AFRT ��� */
  int scan_rpm_;		/*!< SCAN ��� */
} urg_parameter_t;

#endif /* !QRK_URG_PARAMETER_T_H */
