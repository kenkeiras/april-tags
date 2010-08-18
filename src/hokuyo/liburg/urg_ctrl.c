/*!
  \file
  \brief URG 制御

  \author Satofumi KAMIMURA

  $Id: urg_ctrl.c 882 2009-05-13 19:30:41Z satofumi $
*/

#include "math_utils.h"
#include "urg_ctrl.h"
#include "scip_handler.h"
#include "urg_errno.h"
#include "serial_ctrl.h"
#include "serial_utils.h"
#include "serial_errno.h"
#include "getTicks.h"
#include "delay.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>


#if defined(WINDOWS_OS)
#define snprintf _snprintf
#endif

enum {
  ScipTimeout = 1000,           /*!< [msec] */
  EachTimeout = 100,		/*!< [msec] */
};


/* 接続処理
 en: connection handling
*/
static int urg_firstConnection(urg_t *urg, long baudrate)
{
  long try_baudrates[] = { 115200, 19200, 38400 };
  int try_size = sizeof(try_baudrates) / sizeof(try_baudrates[0]);
  int pre_ticks;
  int reply = 0;
  int ret;
  int i;

  /* 接続したいボーレートを配列の先頭と入れ換える
   en: beginning of the array and replace the baud rate you want to connect */
  for (i = 1; i < try_size; ++i) {
    if (baudrate == try_baudrates[i]) {
      long swap_tmp = try_baudrates[i];
      try_baudrates[i] = try_baudrates[0];
      try_baudrates[0] = swap_tmp;
      break;
    }
  }

  /* 指定のボーレートで接続し、応答が返されるかどうか試す
   en: Connect at the specified baud rate, test whether the response is received*/
  for (i = 0; i < try_size; ++i) {

    /* ホスト側のボーレートを変更
     en: Change baud rate */
    ret = serial_setBaudrate(&urg->serial_, try_baudrates[i]);
    if (ret < 0) {
      return ret;
    }

    serial_clear(&urg->serial_);

    /* QT の発行
     en: QT publish
    */
    ret = scip_qt(&urg->serial_, &reply, ScipWaitReply);
    if (ret == UrgSerialRecvFail) {
      /* 応答が返されない場合、ボーレートが違うとみなす */
      continue;
    }

    if ((ret == UrgMismatchResponse) && (reply != -0xE)) {
      /* MD/MS コマンドの応答を受け取ったときの処理 */
      /* 受信内容を全て読み飛ばしてから、次の処理を行う */
      /* (reply == -0xE) のときは、SCIP1.1 応答で 'E' の場合

       en:
       * MD / MS process when it receives a command response
       * skip all the contents from the receiver to do the following:
       (reply ==-0xE) is when , SCIP1.1 response 'E' if
      */
      serial_clear(&urg->serial_);
      serial_skip(&urg->serial_, ScipTimeout, EachTimeout);
      reply = 0x00;
    }

    /* 応答が返されれば、既に SCIP2.0 モードであり "SCIP2.0" の発行は不要

       en: If the response is returned , which already SCIP2.0 mode
       "SCIP2.0" without the issuance of
    */
    if (reply != 0x00) {
      if ((ret = scip_scip20(&urg->serial_)) < 0) {
        /* 応答がなければ、違うボーレートに接続したものとみなす

         en: If no response is deemed to have connected to a different baud rate
        */
        continue;
      }
      if (ret == 12) {
        /* SCIP1.1 プロトコル
         en: SCIP1.1 Protocol*/
        return UrgScip10;
      }
    }

    /* ボーレートを変更する必要がなければ、戻る
     en: If there is no need to change the baud rate back
    */
    if (baudrate == try_baudrates[i]) {
      return 0;
    }

    /* URG 側を指定されたボーレートに変更する
       en: URG to change the baud rate specified
    */
    pre_ticks = getTicks();
    if (scip_ss(&urg->serial_, baudrate) < 0) {
      return UrgSsFail;

    } else {
      /* シリアル通信の場合、ボーレート変更後、１周分だけ待つ必要がある
       en: For serial communications , after changing the baud rate ,
       have to wait only one around */
      int reply_msec = getTicks() - pre_ticks;
      delay((reply_msec * 4 / 3) + 10);

      return serial_setBaudrate(&urg->serial_, baudrate);
    }
  }

  return UrgAdjustBaudrateFail;
}


static void urg_t_initialize(urg_t *urg)
{
  urg->parameters_.area_max_ = 0;
  urg->parameters_.scan_rpm_ = 0;
  urg->parameters_.sensor_type[0] = '\0';
}


/* シリアルデバイスを開き、URG との初期化を行う
 EN:  Open a serial device , URG and initialization */
int urg_connect(urg_t *urg, const char *device, long baudrate)
{
  int ret;
  urg_t_initialize(urg);

  /* シリアル接続を開く
   en: Open serial connection
  */
  ret = serial_connect(&urg->serial_, device, baudrate);
  if (ret != 0) {
    urg->errno_ = UrgSerialConnectionFail;
    return ret;
  }

  /* URG への接続処理
     en:process of connecting to
  */
  ret = urg_firstConnection(urg, baudrate);
  if (ret < 0) {
    urg->errno_ = ret;
    serial_disconnect(&urg->serial_);
    return ret;
  }

  /* パラメータ情報の更新、および初期化
   en: Updating parameter information , and initialize */
  ret = scip_pp(&urg->serial_, &urg->parameters_);
  if (ret < 0) {
    urg->errno_ = ret;
    serial_disconnect(&urg->serial_);
    return ret;
  }
  urg->skip_lines_ = 1;
  urg->skip_frames_ = 0;
  urg->capture_times_ = 0;
  urg->is_laser_on_ = UrgLaserUnknown;
  urg->remain_times_ = 0;

  urg->errno_ = UrgNoError;
  return 0;
}


void urg_disconnect(urg_t *urg)
{
  /* MD/MS コマンドを停止させるため */
  urg_laserOff(urg);
  serial_skip(&urg->serial_, ScipTimeout, EachTimeout);

  /* シリアル接続の切断 */
  serial_disconnect(&urg->serial_);
}


int urg_isConnected(urg_t *urg)
{
  /* シリアル接続が有効ならば、0 を返す */
  return serial_isConnected(&urg->serial_);
}


const char *urg_error(urg_t *urg)
{
  return urg_strerror(urg->errno_);
}


int urg_versionLines(urg_t *urg, char* lines[], int lines_max)
{
  return scip_vv(&urg->serial_, lines, lines_max);
}


/* PP コマンドを送信し、応答を解析して格納してから返す
 PP sends a command to return to store and analyze the response
*/
int urg_parameters(urg_t *urg, urg_parameter_t* parameters)
{
  int ret = 0;

  if (urg_isConnected(urg)) {
    *parameters = urg->parameters_;
  } else {
    ret = scip_pp(&urg->serial_, &urg->parameters_);
    if (parameters) {
      *parameters = urg->parameters_;
    }
  }

  urg->errno_ = UrgNoError;
  return 0;
}


char* urg_model(urg_t *urg)
{
  return urg->parameters_.sensor_type;
}


int urg_dataMax(urg_t *urg)
{
  return urg->parameters_.area_max_ + 1;
}


int urg_scanMsec(urg_t *urg)
{
  int scan_rpm = urg->parameters_.scan_rpm_;
  return (scan_rpm <= 0) ? 1 : (1000 * 60 / scan_rpm);
}


long urg_maxDistance(urg_t *urg)
{
  return urg->parameters_.distance_max_;
}


long urg_minDistance(urg_t *urg)
{
  return urg->parameters_.distance_min_;
}


int urg_setSkipLines(urg_t *urg, int lines)
{
  /* 間引きライン数を登録
   en: register the number of thinning lines
  */
  if (lines == 0) {
    lines = 1;
  }
  if ((lines >= 0) && (lines <= 99)) {
    urg->skip_lines_ = lines;
  }

  return 0;
}


int urg_setSkipFrames(urg_t *urg, int frames)
{
  /* 間引きフレーム数を登録  */
    /* number of frames decimated Register */

  urg->skip_frames_ = frames;

  return 0;
}


int urg_setCaptureTimes(urg_t *urg, int times)
{
    /* MD/MS のデータ取得回数を登録 */
    /* MD / MS data acquisition registration number */
  if ((times < 0) || (times >= 100)) {
    urg->capture_times_ = 0;
  } else {
    urg->capture_times_ = times;
  }

  return 0;
}


int urg_remainCaptureTimes(urg_t *urg)
{
  if (urg->capture_times_ == 0) {
    /* 無限回のデータ取得
     EN: infinite time data acquisition */
    return 100;

  } else {
    return urg->remain_times_;
  }
}


int urg_requestData(urg_t *urg,
                    urg_request_type request_type,
                    int first_index,
                    int last_index)
{
  char buffer[] = "MDsssseeeellstt\r";

  if ((first_index == URG_FIRST) && (last_index == URG_LAST)) {
    first_index = 0;
    last_index = urg->parameters_.area_max_;
  }

  if ((request_type == URG_GD) || (request_type == URG_GS) ||
      (request_type == URG_GD_INTENSITY)) {

    /* GD/GS の場合 */
    snprintf(buffer, 14, "G%c%04d%04d%02d\r",
             (((request_type == URG_GD) ||
               (request_type == URG_GD_INTENSITY)) ? 'D' : 'S'),
             first_index, last_index,
             urg->skip_lines_);

    /* レーザが点灯してない場合、点灯を指示する */
    if (urg->is_laser_on_ != UrgLaserOn) {
      urg_laserOn(urg);
    }

  } else if ((request_type == URG_MD) || (request_type == URG_MS) ||
             (request_type == URG_MD_INTENSITY)) {
    char type = (request_type == URG_MS) ? 'S' : 'D';

    /* MD/MS の場合 */
    snprintf(buffer, 17, "M%c%04d%04d%02d%d%02d\r",
             type,
             first_index, last_index,
             urg->skip_lines_,
             urg->skip_frames_,
             urg->capture_times_);
    urg->remain_times_ = urg->capture_times_;

  } else {
    urg->errno_ = UrgInvalidArgs;;
    return urg->errno_;
  }

  if ((request_type == URG_GD_INTENSITY) ||
      (request_type == URG_MD_INTENSITY)) {
    if (! strcmp("UTM-30LX", urg->parameters_.sensor_type)) {
      if (request_type == URG_GD_INTENSITY) {
        urg->errno_ = UtmNoGDIntensity;
        return urg->errno_;
      }
      /* Top-URG は ME コマンドを使う */
      buffer[0] = 'M';
      buffer[1] = 'E';

      /* グループ数を 2 固定にする */
      buffer[10] = '0';
      buffer[11] = '2';

    } else {
      /* Top-URG 以外の URG はまとめる数に特殊文字を使って強度データを受け取る*/
      buffer[10] = 'F';
      buffer[11] = 'F';
    }
  }

  return scip_send(&urg->serial_, buffer);
}


/* URG の 6bit データをデコード */
static long decode(const char* data, int data_byte) {

  long value = 0;
  int i;

  for (i = 0; i < data_byte; ++i) {
    value <<= 6;
    value &= ~0x3f;
    value |= data[i] - 0x30;
  }
  return value;
}


static int convertRawData(long data[], int data_max,
                          const char* buffer, int buffer_size, int filled,
                          int is_me_type,
                          int data_bytes, int skip_lines,
                          int current_first, int store_first, int store_last,
                          long intensity_data[])
{
  static char remain_data[3];
  static int remain_byte = 0;

  int store_offset = current_first - store_first;
  int n;
  int i;
  int j;

  (void)intensity_data;

  /* store_first, store_last でデータサイズを制限する */
  if (data_max > store_last) {
    data_max = store_last;
  }

  if (filled == 0) {
    /* 最初の呼び出しのときに、残りデータ数の初期化を行う */
    remain_byte = 0;
  }

  /* 直前の処理データが残っていれば、それを処理する */
  if (remain_byte > 0) {
    memcpy(&remain_data[remain_byte], buffer, data_bytes - remain_byte);
    for (j = 0; j < skip_lines; ++j) {
      int store_index = filled - store_offset;
      if (store_index >= data_max) {
        return store_index;
      }
      data[store_index] = decode(remain_data, data_bytes);
      ++filled;
    }

    if (is_me_type) {
      for (j = 0; j < skip_lines; ++j) {
        // !!!
      }
    }
  }

  /* １行のデータを処理する */
  n = buffer_size - data_bytes;
  for (i = (data_bytes - remain_byte) % data_bytes; i < n; i += data_bytes) {
    for (j = 0; j < skip_lines; ++j) {
      int store_index = filled - store_offset;
      if (store_index >= data_max) {
        return store_index;
      }
      data[store_index] = decode(&buffer[i], data_bytes);
      ++filled;
    }

    if (is_me_type) {
      for (j = 0; j < skip_lines; ++j) {
        // !!!
      }
    }
  }

  /* 残ったデータを退避する */
  remain_byte = buffer_size - i;
  memcpy(remain_data, &buffer[i], remain_byte);

  return filled;
}


static int checkSum(char buffer[], int size, char actual_sum)
{
  char expected_sum = 0x00;
  int i;

  for (i = 0; i < size; ++i) {
    expected_sum += buffer[i];
  }
  expected_sum = (expected_sum & 0x3f) + 0x30;

  return (expected_sum == actual_sum) ? 0 : -1;
}


static int atoi_substr(const char *str, size_t len)
{
  char buffer[13];

  strncpy(buffer, str, len);
  buffer[len] = '\0';

  return atoi(buffer);
}


static int internal_receiveData(urg_t *urg, long data[], int data_max,
                                int store_first, int store_last,
                                long intensity_data[])
{
  enum {
    EchoBack = 0,
    ReplyCode,
    Timestamp,

    False = 0,
    True = 1,

    MD_MS_Length = 15,          /* MD, MS コマンドの長さ (en: MD, MS command length) */
    GD_GS_Length = 12,          /* GD, GS コマンドの長さ (en: GD, GS command length*/
  };

  int lines = 0;
  char buffer[UrgLineWidth];
  int filled = 0;
  int is_header = False;
  int n;

  int is_me_type = 0;
  char current_type[] = "xx";
  int current_first = -1;
  int current_last = -1;
  int current_skip_lines = -1;
  int current_skip_frames = -1;
  int current_capture_times = -1;
  int current_data_bytes = 3;

  /* タイムスタンプを初期化
   en: Initialization time stamp
  */
  urg->last_timestamp_ = UrgInvalidTimestamp;

  urg->errno_ = UrgNoResponse;

  while (1) {
    n = serial_getLine(&urg->serial_, buffer, ScipLineWidth, ScipTimeout);
    //fprintf(stderr, "%d: %s\n", lines, buffer);
    if (n <= 0) {
      if (is_header) {
        /* !!! 制御構造を見直す
         en: control overhaul */
        is_header = False;
        lines = 0;
        continue;
      }
      break;
    }

    if (lines > 0) {
      if (checkSum(buffer, n - 1, buffer[n - 1]) < 0) {
        urg->errno_ = UrgInvalidResponse;

        //return UrgInvalidResponse;
        // !!! URG のパケットエラーがなくなったら、この実装に戻す

        // !!! 現状では、エラーが起きたら連続した改行までを読み飛ばす
        // !!! ようにしたい
        lines = 0;
        filled = 0;
        is_header = False;
        continue;
      }
    }

    switch (lines) {
    case EchoBack:
      /* エコーバック */

      if ((n != GD_GS_Length) && (n != MD_MS_Length)) {
        /* GD/GS, MD/MS 以外の応答のとき、戻る */
        urg->errno_ = UrgInvalidResponse;
        //return -1;
        // !!! URG のパケットエラーがなくなったら、この実装に戻す

        lines = 0;
        filled = 0;
        is_header = False;
        continue;
      }
      /* 応答コマンド */
      current_type[0] = buffer[0];
      current_type[1] = buffer[1];
      if (! strncmp("ME", current_type, 2)) {
        is_me_type = 1;
      }

      /* 取得設定の初期化 */
      current_first = atoi_substr(&buffer[2], 4);
      current_last = atoi_substr(&buffer[6], 4);
      current_skip_lines = atoi_substr(&buffer[10], 2);

      if ((current_first - store_first) >= data_max) {
        /* 取得範囲が、データサイズに含まれていない */
        return 0;
      }

      /* ダミーデータの配置 */
      for (filled = 0; filled < (current_first - store_first); ++filled) {
        int store_index = filled - (current_first - store_first);
        data[store_index] = 0;
      }

      if (n == GD_GS_Length) {
        /* GD/GS コマンドのときは、取得フレーム設定と取得回数設定は無視される */
        urg->remain_times_ = 0;

      } else {
        current_skip_frames = atoi_substr(&buffer[12], 1);
        current_capture_times = atoi_substr(&buffer[13], 2);

        /* MD/MS のときに、残りスキャン回数を格納する */
        urg->remain_times_ = atoi(&buffer[13]);
      }
      current_data_bytes = (current_type[1] == 'S') ? 2 : 3;
      break;

    case ReplyCode:
      /* 応答 */
      /* !!! 00 以外だとエラー、戻る。*/
      /* !!! */

      /* MD/MS で "00" のときは、送信要求に対する応答なので、*/
      /* もう１行読み出してから、処理をリセットする */
      if (current_type[0] == 'M' && (! strncmp(buffer, "00", 2))) {
        is_header = True;
      }

      /* !!! "99b" のときが実際のデータ */
      /* if (! strcmp(buffer, "99b")) { */
      /* } */
      break;

    case Timestamp:
      /* タイムスタンプ */
      urg->last_timestamp_ = decode(buffer, 4);
      break;

    default:
      /* データの変換処理 */
      filled = convertRawData(data, data_max, buffer, n - 1, filled,
                              is_me_type,
                              current_data_bytes, urg->skip_lines_,
                              current_first, store_first, store_last,
                              intensity_data);
      break;
    }
    ++lines;
  }

  /* !!! QT のときは、QT だったとわかる応答を返す */

  /* !!! 要求したデータ受信を行う */
  /* !!! 直前で格納したデータ情報を urg に格納してしまう */

  if (filled <= 0) {
    return urg->errno_;
  } else {
    return filled;
  }
}


/* データ受信 */
int urg_receiveData(urg_t *urg, long data[], int data_max)
{
  return internal_receiveData(urg, data, data_max, 0, data_max, NULL);
}


#if defined(USE_INTENSITY)
int urg_receiveDataWithIntensity(urg_t *urg, long data[], int data_max,
                                 long intensity[])
{
  int i;
  int n;

  n = internal_receiveData(urg, data, data_max, 0, data_max, intensity);

  for (i = 0; i < n; i += 2) {
    long length = data[i];

    if ((i + 1) < data_max) {
      long intensity_value = data[i + 1];
      intensity[i] = intensity_value;
      intensity[i + 1] = intensity_value;
      data[i + 1] = length;
    }
  }
  return n;
}
#endif


int urg_receivePartialData(urg_t *urg, long data[], int data_max,
                           int first_index, int last_index)
{
  return internal_receiveData(urg, data, data_max,
                              first_index, last_index, NULL);
}


long urg_recentTimestamp(urg_t *urg)
{
  /* 直前のデータのタイムスタンプを返す
   (english) Return the timestamp of the last data
  */
  return urg->last_timestamp_;
}


double urg_index2rad(urg_t *urg, int index)
{
  double radian = (2.0 * M_PI) *
    (index - urg->parameters_.area_front_) / urg->parameters_.area_total_;

  return radian;
}


int urg_index2deg(urg_t *urg, int index)
{
  int degree = (int)floor((urg_index2rad(urg, index) * 180 / M_PI) + 0.5);

  return degree;
}


int urg_rad2index(urg_t *urg, double radian)
{
  int index =
    (int)floor((((radian * urg->parameters_.area_total_) / (2.0*M_PI))
                + urg->parameters_.area_front_) + 0.5);

  if (index < 0) {
    index = 0;
  } else if (index > urg->parameters_.area_max_) {
    index = urg->parameters_.area_max_;
  }
  return index;
}


int urg_deg2index(urg_t *urg, int degree)
{
  return urg_rad2index(urg, M_PI * degree / 180.0);
}


int urg_laserOn(urg_t *urg)
{
  /* BM の送信 */
  int expected_ret[] = { 0, 2, -1 };
  int send_n = scip_send(&urg->serial_, "BM\r");
  if (send_n != 3) {
    /* !!! urg->errno = UrgSendFail; */
    return SerialSendFail;
  }
  if (scip_recv(&urg->serial_, "BM", NULL, expected_ret, ScipTimeout) == 0) {
    urg->is_laser_on_ = UrgLaserOn;
  }

  return 0;
}


int urg_laserOff(urg_t *urg)
{
  /* QT の送信 */
  return scip_qt(&urg->serial_, NULL, ScipWaitReply);
}


int urg_enableTimestampMode(urg_t *urg)
{
  /* TM0 の送信
     en: send TM0
  */
  int expected_ret[] = { 0, 2, -1 };
  int send_n = scip_send(&urg->serial_, "TM0\r");
  if (send_n != 4) {
    /* !!! urg->errno = UrgSendFail; */
    return SerialSendFail;
  }
  return scip_recv(&urg->serial_, "TM", NULL, expected_ret, ScipTimeout);
}


int urg_disableTimestampMode(urg_t *urg)
{
  /* TM2 の送信
   en: send TM2
  */
  int expected_ret[] = { 0, 3, -1 };
  int send_n = scip_send(&urg->serial_, "TM2\r");
  if (send_n != 4) {
    /* !!! urg->errno = UrgSendFail; */
    return SerialSendFail;
  }
  return scip_recv(&urg->serial_, "TM", NULL, expected_ret, ScipTimeout);
}


/** synchronously queries the URG for its timestamp. **/
long urg_currentTimestamp(urg_t *urg)
{
  char buffer[ScipLineWidth];
  long timestamp = -1;
  int ret = 0;
  int n;

  /* TM1 の送信
   en: send TM1
  */
  int expected_ret[] = { 0, -1 };
  int send_n = scip_send(&urg->serial_, "TM1\r");
  if (send_n != 4) {
    /* !!! urg->errno = UrgSendFail; */
    return SerialSendFail;
  }
  ret = scip_recv(&urg->serial_, "TM", NULL, expected_ret, ScipTimeout);
  if (ret != 0) {
    return ret;
  }

  /* タイムスタンプをデコードして返す
   en: decode time stamp and return */
  n = serial_getLine(&urg->serial_, buffer, ScipLineWidth, ScipTimeout);
  if (n == 5) {
    timestamp = decode(buffer, 4);
  }

  /* 最後の応答を読み捨て
     Last response読Mi捨Te
  */
  n = serial_recv(&urg->serial_, buffer, 1, ScipTimeout);
  if (! serial_isLF(buffer[0])) {
    serial_ungetc(&urg->serial_, buffer[0]);
  }

  return timestamp;
}
