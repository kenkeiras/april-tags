package april.jserial;

import java.io.*;

public class JSerial
{
    protected static native int serial_open_jni(String url, int baud, int blocking);
    protected static native int serial_set_baud_jni(int fd, int baudrate);
    protected static native int serial_set_ctsrts_jni(int fd, int enable);
    protected static native int serial_set_xon_jni(int fd, int enable);
    protected static native int serial_set_mode_jni(int fd, int databits, int parity, int stopbits);
    protected static native int serial_set_dtr_jni(int fd, int v);
    protected static native int serial_set_rts_jni(int fd, int v);
    protected static native int serial_close_jni(int fd);

    static native int write(int fd, byte buf[], int offset, int len);
    static native int read(int fd, byte buf[], int offset, int len);

    static native int readTimeout(int fd, byte buf[], int offset, int len, int mstimeout);
    static native int readTimeoutFully(int fd, byte buf[], int offset, int len, int mstimeout);
    static native int readAvailable(int fd);

    int fd;

    static {
        System.loadLibrary("jserial");
    }

    public JSerial(String url, int baudrate, String mode, int blocking) throws IOException
    {
        fd = serial_open_jni(url, baudrate, blocking);
        setMode(mode);

        if (fd < 0)
            throw new IOException("Couldn't open serial port");
    }

    public JSerial(String url, int baudrate, String mode) throws IOException
    {
        fd = serial_open_jni(url, baudrate, 1);
        setMode(mode);

        if (fd < 0)
            throw new IOException("Couldn't open serial port");
    }

    public JSerial(String url, int baudrate) throws IOException
    {
        fd = serial_open_jni(url, baudrate, 1);

        if (fd < 0)
            throw new IOException("Couldn't open serial port");
    }

    public int getFd()
    {
        return fd;
    }

    public int readFully(byte buf[], int offset, int len)
    {
        int sofar = 0;
        while (sofar < len) {
            int res = read(fd, buf, offset + sofar, len - sofar);
            if (res < 0)
                return res;
            sofar += res;
        }
        return sofar;
    }

    /** How many bytes are available to be read without blocking? **/
    public int available()
    {
        return readAvailable(fd);
    }

    public int readTimeout(byte buf[], int offset, int len, int mstimeout)
    {
        return readTimeout(fd, buf, offset, len, mstimeout);
    }

    public int readFullyTimeout(byte buf[], int offset, int len, int mstimeout)
    {
        return readTimeoutFully(fd, buf, offset, len, mstimeout);
    }

    public int read(byte buf[], int offset, int len)
    {
        return read(fd, buf, offset, len);
    }

    public int write(String s)
    {
        byte b[] = s.getBytes();
        return write(b, 0, b.length);
    }

    public int write(byte buf[], int offset, int len)
    {
        return write(fd, buf, offset, len);
    }

    public void setBaud(int baudrate) throws IOException
    {
        int res = serial_set_baud_jni(fd, baudrate);
        if (res != 0)
            throw new IOException("Could not set baudrate: "+baudrate);
    }

    public void setCTSRTS(boolean v)
    {
        serial_set_ctsrts_jni(fd, v ? 1 : 0);
    }

    public void setDTR(boolean v)
    {
        serial_set_dtr_jni(fd, v ? 1 : 0);
    }

    public void setRTS(boolean v)
    {
        serial_set_rts_jni(fd, v ? 1 : 0);
    }

    /** Mode e.g. 8N1, 7E1 **/
    public void setMode(String mode)
    {
        assert(mode.length()==3);
        int databits = 8;
        int parity = 0;
        int stopbits = 1;

        switch (mode.charAt(0))
	    {
            case '5':
            case '6':
            case '7':
            case '8':
                databits = mode.charAt(0)-'0';
                break;
            default:
                assert(false);
	    }

        switch (Character.toUpperCase(mode.charAt(1)))
	    {
            case 'N':
                parity = 0;
                break;
            case 'O':
                parity = 1;
                break;
            case 'E':
                parity = 2;
                break;
            default:
                assert(false);
                break;
	    }

        switch (mode.charAt(2))
	    {
            case '1':
            case '2':
                stopbits = mode.charAt(2)-'0';
                break;
            default:
                assert(false);
	    }

        setMode(databits, parity, stopbits);
    }

    public void setMode(int databits, int parity, int stopbits)
    {
        serial_set_mode_jni(fd, databits, parity, stopbits);
    }

    public void close()
    {
        serial_close_jni(fd);
    }

}
