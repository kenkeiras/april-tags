package april.jcam;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import april.util.*;

/* EXPERIMENTAL ImageSource. Listens for images over a TCP connection using the format below.
   Intended for use as glue code to transfer images over localhost with arbitrary drivers.

   Example url to listen on port 7001: tcp://7001

   Format:
    - sync word     (8 bytes, 0x17923349ab10ea9aL )
    - utime         (8 bytes)
    - width         (4 bytes)
    - height        (4 bytes)
    - format length (4 bytes)
    - format string (format length bytes)
    - buffer length (4 bytes)
    - buffer        (buffer length bytes)
 */
public class ImageSourceTCP extends ImageSource
{
    public static final long MAGIC = 0x17923349ab10ea9aL;

    ReceiveThread rthread;
    Integer port;

    final static int MAX_QUEUE_SIZE = 100;
    ArrayBlockingQueue<FrameData> queue = new ArrayBlockingQueue<FrameData>(MAX_QUEUE_SIZE*2);
    int numDropped = 0;

    ImageSourceFormat lastFmt;
    boolean warned = false;

    public ImageSourceTCP(String url)
    {
        assert(url.startsWith("tcp://"));

        url = url.substring("tcp://".length());
        int argidx = url.indexOf("?");
        if (argidx >= 0) {
            String arg = url.substring(argidx+1);
            url = url.substring(0, argidx);
        }

        port = Integer.valueOf(url);

        rthread = new ReceiveThread();
        rthread.start();
    }

    ////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////

    public boolean isFeatureAvailable(int idx)
    {
        return false;
    }

    public String getFeatureType(int idx)
    {
        return "";
    }

    /** Wait for a new image or use the last unused image if one exists. Return the
      * byte buffer and save ImageSourceFormat and timestamp for later.
      * <br>
      */
    public FrameData getFrame()
    {
        FrameData fd = null;

        try {
            fd = queue.take();
        } catch (Exception ex) {
            System.out.println("Exception during ArrayBlockingQueue.take(): " + ex);
        }

        if (fd != null)
            lastFmt = fd.ifmt;

        return fd;
    }

    /** Returns the LAST image's format. Returns null if no image has been read.
      */
    public ImageSourceFormat getFormat(int idx)
    {
        assert(idx == 0);

        // if we don't have a format, we have to return null
        if (lastFmt == null) {
            if (!warned) {
                warned = true;
                System.out.println("Warning: ImageSourceTCP.getFormat() returning null because no frame has been received");
            }

            return null;
        }

        return lastFmt;
    }

    ////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////

    public void start()
    {
    }

    public void stop()
    {
    }

    public int getNumFormats()
    {
        return 1;
    }

    public void setFormat(int idx)
    {
        assert(idx == 0);
    }

    public int getCurrentFormatIndex()
    {
        return 0;
    }

    public void printInfo()
    {
        System.out.printf("========================================\n");
        System.out.printf(" ImageSourceTCP Info\n");
        System.out.printf("========================================\n");
        System.out.printf("\tPort: %d\n", port);
    }

    public int close()
    {
        return 0;
    }

    ////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////

    private class ReceiveThread extends Thread
    {
        ReceiveThread()
        {
            this.setName("ImageSourceTCP ReceiveThread");
        }

        public void run()
        {
            assert(port != null);

            try {

                ServerSocket serverSock = new ServerSocket(port);

                while (true) {
                    System.out.println("Waiting for connection...");
                    Socket sock = serverSock.accept();
                    System.out.println("Connected");
                    readSock(sock);
                }

            } catch (IOException ex) {
                System.out.println("ex: "+ex);
                System.exit(1);
            }
        }

        void readSock(Socket sock)
        {
            try {
                DataInputStream ins = new DataInputStream(new BufferedInputStream(sock.getInputStream()));
                long magic = 0;

                while (true) {

                    int v = ins.readByte() & 0xFF;
                    magic = (magic << 8) | v;

                    if (magic != MAGIC)
                        continue;

                    FrameData fd = new FrameData();

                    // utime
                    fd.utime = ins.readLong();

                    // format
                    fd.ifmt = new ImageSourceFormat();

                    fd.ifmt.width  = ins.readInt();
                    fd.ifmt.height = ins.readInt();

                    byte strbuf[] = new byte[ins.readInt()];
                    ins.readFully(strbuf);
                    fd.ifmt.format = new String(strbuf);

                    // image buffer
                    fd.data = new byte[ins.readInt()];
                    ins.readFully(fd.data);

                    // add the queue
                    synchronized (queue) {

                        queue.add(fd);

                        while (queue.size() > MAX_QUEUE_SIZE) {
                            try {
                                queue.take();
                                numDropped++;
                            } catch (Exception ex) {
                                System.out.println("Exception while shrinking queue: " + ex);
                            }
                        }
                    }
                }

            } catch (EOFException ex) {
                System.out.println("Client disconnected");
            } catch (IOException ex) {
                System.out.println("ex: "+ex); ex.printStackTrace();
            }

            try {
                sock.close();
            } catch(IOException ex) {
                System.out.println("ex: "+ex);
            }
        }
    }
}
