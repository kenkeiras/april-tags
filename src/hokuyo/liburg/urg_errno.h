#ifndef QRK_C_URG_ERRNO_H
#define QRK_C_URG_ERRNO_H

/*!
  \file
  \brief URG のエラーコード

  \author Satofumi KAMIMURA

  $Id: urg_errno.h 783 2009-05-05 08:56:26Z satofumi $
*/


enum {
  UrgNoError = 0,               /*!< 正常 */
  UrgNotImplemented = -1,       /*!< 未実装 */
  UrgSendFail = -2,
  UrgRecvFail = -3,
  UrgScip10 = -4,               /*!< SCIP1.0 応答 */
  UrgSsFail = -5,               /*!< SS コマンド応答に失敗 */
  UrgAdjustBaudrateFail = -6,   /*!< ボーレート合わせに失敗 */
  UrgInvalidArgs = -7,          /*!< 不正な引数指定 */
  UrgInvalidResponse = -8,      /*!< URG 側の応答エラー */
  UrgSerialConnectionFail = -9, /*!< シリアル接続に失敗 */
  UrgSerialRecvFail = -10,      /*!< シリアル接続に失敗 */
  UrgMismatchResponse = -11,    /*!< エコーバック応答が異なる */
  UrgNoResponse = -12,          /*!< 応答なし */
  UtmNoGDIntensity = -13, /*!< UTM-30LX は GD で強度データは取得できない */
};


/*!
  \brief エラーを示す文字列を返す

  \param[in] urg_errno URG のエラー戻り値

  \return エラーを示す文字列
*/
extern const char* urg_strerror(int urg_errno);

#endif /* !QRK_C_URG_ERRNO_H */
