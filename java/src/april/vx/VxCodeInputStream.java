package april.vx;

public class VxCodeInputStream
{

    int pos, startpos, endpos;
    byte buf[];

    public VxCodeInputStream(byte _buf[])
    {
        buf = _buf;
        endpos = buf.length + 1;
    }
    public VxCodeInputStream(byte _buf[], int _offset, int _len)
    {
        buf = _buf;
        startpos = pos = _offset;
        endpos = startpos + _len + 1;
    }

    void needInput(int needed)
    {
        assert(pos + needed < endpos);
    }


    public int available()
    {
        return endpos - pos - 1;
    }

    public void reset()
    {
        pos = startpos;
    }

    public int readInt()
    {
        needInput(4);
        return
            ((buf[pos++]&0xff) << 24) |
            ((buf[pos++]&0xff) << 16) |
            ((buf[pos++]&0xff) << 8) |
            ((buf[pos++]&0xff) << 0);
    }

    public long readLong()
    {
        needInput(8);
        return
            ((buf[pos++]&0xffL) << 56) |
            ((buf[pos++]&0xffL) << 48) |
            ((buf[pos++]&0xffL) << 40) |
            ((buf[pos++]&0xffL) << 32) |
            ((buf[pos++]&0xffL) << 24) |
            ((buf[pos++]&0xffL) << 16) |
            ((buf[pos++]&0xffL) << 8) |
            ((buf[pos++]&0xffL) << 0);
    }

    public float readFloat()
    {
        return Float.intBitsToFloat(readInt());
    }

    public void readFully(byte b[])
    {
        needInput(b.length);
        System.arraycopy(buf, pos, b, 0, b.length);
        pos += b.length;

    }

    /** Read a string of 8-bit characters terminated by a zero. The zero is consumed. **/
    public String readStringZ()
    {
        StringBuffer sb = new StringBuffer();
        while (true) {
            int v = buf[pos++]&0xff;
            if (v == 0)
                break;
            sb.append((char) v);
        }

        return sb.toString();
    }


}