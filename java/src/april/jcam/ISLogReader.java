package april.jcam;

import java.io.*;

import april.util.*;

public class ISLogReader
{
    // XXX should we use the buffered version from lcm-java?
    RandomAccessFile raf;

    public static final long ISMAGIC = 0x17923349ab10ea9aL;
    String path;

    public ISLogReader(String path) throws IOException
    {
        this.path = path;
        raf = new RandomAccessFile(path, "r");
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

    public synchronized void close() throws IOException
    {
        raf.close();
    }
}
