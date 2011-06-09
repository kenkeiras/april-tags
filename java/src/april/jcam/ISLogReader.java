package april.jcam;

import java.io.*;

import april.util.*;

import lcm.util.BufferedRandomAccessFile;

public class ISLogReader
{
    // XXX should we use the buffered version from lcm-java?
    BufferedRandomAccessFile raf;

    public static final long ISMAGIC = 0x17923349ab10ea9aL;
    String path;

    public ISLogReader(String path, String mode) throws IOException
    {
        this.path = path;
        raf = new BufferedRandomAccessFile(path, mode);
    }

    /**
     * Retrieves the path to the log file.
     * @return the path to the log file
     */
    public String getPath()
    {
        return path;
    }

    public static class ISEvent
    {
        /**
         * Byte offset in ISLog file for the start of this event
         **/
        public long                 byteOffset;
        /**
         * Time of message receipt, represented in microseconds since 00:00:00
         * UTC January 1, 1970.
         */
        public long                 utime;
        /**
         * Image format (width, height, encoding)
         **/
        public ImageSourceFormat    ifmt;
        /**
         * Image buffer
         **/
        public byte[]               buf;
    }

    public synchronized ISEvent readNext() throws IOException
    {
        long magic = 0;
        ISEvent e = new ISEvent();

        while (true)
        {
            int v = raf.readByte()&0xff;

            magic = (magic<<8) | v;

            if (magic != ISMAGIC)
                continue;

            // byte offset
            e.byteOffset = raf.getFilePointer() - (long) (Long.SIZE/8);

            // utime
            e.utime = raf.readLong();

            // image source format
            e.ifmt = new ImageSourceFormat();

            e.ifmt.width = raf.readInt();
            e.ifmt.height = raf.readInt();

            byte strbuf[] = new byte[raf.readInt()];
            raf.readFully(strbuf);
            e.ifmt.format = new String(strbuf);

            // image buffer
            e.buf = new byte[raf.readInt()];
            raf.readFully(e.buf);

            break;
        }

        return e;
    }

    /** Get position in percent
      **/
    public synchronized double getPositionFraction() throws IOException
    {
        return raf.getFilePointer()/((double) raf.length());
    }

    /** Jump to position in percent
      **/
    public synchronized void seekPositionFraction(double frac) throws IOException
    {
        raf.seek((long) (raf.length()*frac));
    }

    /** Get position as the RandomAccessFile offset
      **/
    public synchronized long getPosition() throws IOException
    {
        return raf.getFilePointer();
    }

    /** Set position with a RandomAccessFile offset
      **/
    public synchronized void seekPosition(long position) throws IOException
    {
        raf.seek(position);
    }

    public synchronized void close() throws IOException
    {
        raf.close();
    }

    public synchronized long write(ImageSourceFormat ifmt, byte imbuf[]) throws IOException
    {
        long frameStartOffset = raf.getFilePointer();

        raf.writeLong(ISMAGIC);
        raf.writeLong(TimeUtil.utime());
        raf.writeInt(ifmt.width);
        raf.writeInt(ifmt.height);
        raf.writeInt(ifmt.format.length());
        byte strbuf[] = ifmt.format.getBytes();
        raf.write(strbuf, 0, strbuf.length);
        raf.writeInt(imbuf.length);
        raf.write(imbuf, 0, imbuf.length);

        return frameStartOffset;
    }
}
