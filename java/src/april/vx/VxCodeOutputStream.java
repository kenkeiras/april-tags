package april.vx;

import java.io.*;


// Big endian (network byte order)
public final class VxCodeOutputStream
{
    byte buf[];
    int pos;

    public VxCodeOutputStream()
    {
        this(512);
    }

    // initialize the output stream with pre-existing data
    public VxCodeOutputStream(byte buf[])
    {
        this.buf = buf;
        pos = buf.length;
    }

    public VxCodeOutputStream(int initial_size)
    {
        buf = new byte[initial_size];
    }

    void ensureSpace(int needed)
    {
        if (pos+needed >= buf.length) {
            // compute new power-of-two capacity
            int newlen = buf.length;
            while (newlen < pos+needed)
                newlen *= 2;

            byte buf2[] = new byte[newlen];
            System.arraycopy(buf, 0, buf2, 0, pos);
            buf = buf2;
        }
    }

    /** Write a zero-terminated string consisting of 8 bit characters. **/
    public void writeStringZ(String s)
    {
        ensureSpace(s.length()+1);
        for (int i = 0; i < s.length(); i++) {
            buf[pos++] = (byte) s.charAt(i);
        }
        buf[pos++] = 0;
    }

    public void writeFloat(float v)
    {
        writeInt(Float.floatToIntBits(v));
    }

    public void writeInt(int v)
    {
        ensureSpace(4);
        buf[pos++] = (byte) (v>>>24);
        buf[pos++] = (byte) (v>>>16);
        buf[pos++] = (byte) (v>>>8);
        buf[pos++] = (byte) (v>>>0);
    }

    public void writeDouble(double v)
    {
        writeLong(Double.doubleToLongBits(v));
    }

    public void writeLong(long v)
    {
        ensureSpace(8);
        buf[pos++] = (byte) (v>>>56);
        buf[pos++] = (byte) (v>>>48);
        buf[pos++] = (byte) (v>>>40);
        buf[pos++] = (byte) (v>>>32);
        buf[pos++] = (byte) (v>>>24);
        buf[pos++] = (byte) (v>>>16);
        buf[pos++] = (byte) (v>>>8);
        buf[pos++] = (byte) (v>>>0);
    }

    public void write(byte data[], int offset, int length)
    {
        ensureSpace(length);
        System.arraycopy(data, offset, buf, pos, length);
        pos+= length;
    }

    /** Makes a copy of the internal buffer. **/
    public byte[] toByteArray()
    {
        byte b[] = new byte[pos];
        System.arraycopy(buf, 0, b, 0, pos);
        return b;
    }

    /** Returns the internal buffer, which may be longer than the
     * buffer that has been written to so far.
     **/
    public byte[] getBuffer()
    {
        return buf;
    }

    /** Get the number of bytes that have been written to the buffer. **/
    public int size()
    {
        return pos;
    }
}
