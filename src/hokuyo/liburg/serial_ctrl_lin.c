/*!
  \file
  \brief シリアル通信 (Linux, Mac 実装)

  Serial Communication Interface 制御


  \author Satofumi KAMIMURA

  $Id: serial_ctrl_lin.c 882 2009-05-13 19:30:41Z satofumi $
*/

#include "serial_errno.h"
#include "serial_ctrl.h"
#include "serial_t.h"
#include "delay.h"
#include <unistd.h>
#include <stdint.h>
#include <stdio.h>
#include <fcntl.h>
#include <errno.h>
#include <string.h>
#include <sys/file.h>

enum {
  InvalidFd = -1,
};


/* 接続 */
int serial_connect(serial_t *serial, const char *device, long baudrate)
{
  int flags = 0;
  int ret = 0;

#ifndef MAC_OS
  enum { O_EXLOCK = 0x0 }; /* Linux では使えないのでダミーを作成しておく */
#endif
  serial->fd_ = open(device, O_RDWR | O_EXLOCK | O_NONBLOCK | O_NOCTTY);
  if (serial->fd_ < 0) {
    /* 接続に失敗 */
    strerror_r(errno, serial->error_string_, SerialErrorStringSize);
    return SerialConnectionFail;
  }
  if(0 != flock(serial->fd_, LOCK_EX | LOCK_NB)) {
      strerror_r(errno, serial->error_string_, SerialErrorStringSize);
      close(serial->fd_);
      return SerialConnectionFail;
  }

  flags = fcntl(serial->fd_, F_GETFL, 0);
  fcntl(serial->fd_, F_SETFL, flags & ~O_NONBLOCK);

  /* シリアル通信の初期化 */
  tcgetattr(serial->fd_, &serial->sio_);
  serial->sio_.c_iflag = 0;
  serial->sio_.c_oflag = 0;
  serial->sio_.c_cflag &= ~(CSIZE | PARENB | CSTOPB);
  serial->sio_.c_cflag |= CS8 | CREAD | CLOCAL;
  serial->sio_.c_lflag &= ~(ICANON | ECHO | ISIG | IEXTEN);

  serial->sio_.c_cc[VMIN] = 0;
  serial->sio_.c_cc[VTIME] = 0;

  /* ボーレートの変更 */
  ret = serial_setBaudrate(serial, baudrate);
  if (ret < 0) {
    return ret;
  }

  /* シリアル制御構造体の初期化 */
  serial->last_ch_ = '\0';

  serial->buf_head = 0;
  serial->buf_capacity = SERIAL_BUF_CAPACITY;
  serial->buf_filled = 0;

  return 0;
}


/* 切断 */
void serial_disconnect(serial_t *serial)
{
    if (serial->fd_ >= 0) {
        flock(serial->fd_, LOCK_UN | LOCK_NB);
        close(serial->fd_);
        serial->fd_ = InvalidFd;
    }
}


int serial_isConnected(serial_t *serial)
{
  return ((serial == NULL) || (serial->fd_ == InvalidFd)) ? 0 : 1;
}


/* ボーレートの設定 */
int serial_setBaudrate(serial_t *serial, long baudrate)
{
  long baudrate_value = -1;

  switch (baudrate) {
  case 4800:
    baudrate_value = B4800;
    break;

  case 9600:
    baudrate_value = B9600;
    break;

  case 19200:
    baudrate_value = B19200;
    break;

  case 38400:
    baudrate_value = B38400;
    break;

  case 57600:
    baudrate_value = B57600;
    break;

  case 115200:
    baudrate_value = B115200;
    break;

  default:
#if 0
    /* !!! バッファを導入したら、処理させる */
    snprintf(serial->error_message, SerialErrorStringSize,
             "No handle baudrate value: %ld", baudrate);
#endif
    return SerialSetBaudrateFail;
  }

  /* ボーレート変更 */
  cfsetospeed(&serial->sio_, baudrate_value);
  cfsetispeed(&serial->sio_, baudrate_value);
  tcsetattr(serial->fd_, TCSADRAIN, &serial->sio_);

  serial_clear(serial);

  return 0;
}


/* 送信 */
int serial_send(serial_t *serial, const char *data, int data_size)
{
  if (! serial_isConnected(serial)) {
    return SerialConnectionFail;
  }
  return write(serial->fd_, data, data_size);
}


static int waitReceive(serial_t* serial, int timeout)
{
  fd_set rfds;
  struct timeval tv;

  // タイムアウト設定
  FD_ZERO(&rfds);
  FD_SET(serial->fd_, &rfds);

  tv.tv_sec = timeout / 1000;
  tv.tv_usec = (timeout % 1000) * 1000;

  if (select(serial->fd_ + 1, &rfds, NULL, NULL, &tv) <= 0) {
    /* タイムアウト発生 */
    return 0;
  }
  return 1;
}

static int
read_into_buffer(serial_t *serial, int timeout)
{
    if ((timeout > 0) && (! waitReceive(serial, 0))) {
        // スレッド切替えを促す
        delay(1);
    }
    if (! waitReceive(serial, timeout)) {
        return 0;
    }

    int buf_available = serial->buf_capacity - serial->buf_filled;
    char tmp_buf[buf_available];

    int read_n = read(serial->fd_, tmp_buf, buf_available);
    if(read_n <= 0)
        return read_n;

    int head_to_end = serial->buf_capacity - serial->buf_head;
    if(head_to_end < read_n) {
        memcpy(serial->buf + serial->buf_head, tmp_buf, head_to_end);
        int still_to_copy = read_n - head_to_end;
        memcpy(serial->buf, tmp_buf + head_to_end, still_to_copy);
    } else {
        memcpy(serial->buf + serial->buf_head, tmp_buf, read_n);
    }
    serial->buf_filled += read_n;

    return read_n;
}

static int
read_from_buffer(serial_t *serial, char *data, int data_size_max)
{
    if(!serial->buf_filled || data_size_max <= 0)
        return 0;

    int nread = 0;
    // read from head
    int n = serial->buf_capacity - serial->buf_head;
    if(n > serial->buf_filled)
        n = serial->buf_filled;

    if(n > data_size_max)
        n = data_size_max;

    memcpy(data, serial->buf + serial->buf_head, n);
    serial->buf_head += n;
    serial->buf_filled -= n;
    int still_wanted = data_size_max - n;
    nread = n;

    if(serial->buf_head >= serial->buf_capacity) {
        serial->buf_head = 0;
    }

    if(still_wanted && serial->buf_filled) {
        if(serial->buf_filled > still_wanted)
            n = still_wanted;
        else
            n = serial->buf_filled;

        memcpy(data + nread, serial->buf + serial->buf_head, n);
        serial->buf_head += n;
        serial->buf_filled -= n;
        nread += n;
    }
    
    return nread;
}

/* 受信 */
int serial_recv(serial_t *serial, char* data, int data_size_max, int timeout)
{
    if (data_size_max <= 0) {
        return 0;
    }

    /* 書き戻した１文字があれば、書き出す */
    int filled = 0;
    if (serial->last_ch_ != '\0') {
        data[0] = serial->last_ch_;
        serial->last_ch_ = '\0';
        ++filled;
    }

    if (! serial_isConnected(serial)) {
        return SerialConnectionFail;
    }

    // optimization since data_size_max seems to always be 1
    if(1 == data_size_max && !filled && serial->buf_filled) {
        data[0] = serial->buf[serial->buf_head];
        serial->buf_head = (serial->buf_head + 1) % serial->buf_capacity;
        serial->buf_filled--;
        return 1;
    }

    while(filled < data_size_max) {
        filled += read_from_buffer(serial, data + filled, 
                data_size_max - filled);

        if(filled < data_size_max) {
            // XXX timeout handling is incorrect
            int status = read_into_buffer(serial, timeout); 
            if(status <= 0) {
                return filled;
            }
        }
    }
    return filled;
}


/* １文字書き戻す */
void serial_ungetc(serial_t *serial, char ch)
{
  serial->last_ch_ = ch;
}


void serial_clear(serial_t* serial)
{
  tcdrain(serial->fd_);
  tcflush(serial->fd_, TCIOFLUSH);
  serial->last_ch_ = '\0';
  serial->buf_head = 0;
  serial->buf_filled = 0;
}
