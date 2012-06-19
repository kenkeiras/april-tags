package april.jcam;

import java.io.*;
import java.util.*;

import april.util.*;

import lcm.lcm.*;
import lcm.util.*;
import april.lcmtypes.*;

public class ImageSourceISLogLCM extends ImageSource implements LCMSubscriber
{
    LCM lcm = LCM.getSingleton();

    String basepath = null;
    String channel = null;

    BlockingSingleQueue<ISLog.ISEvent> queue = new BlockingSingleQueue<ISLog.ISEvent>();

    ISLog log = null;
    ISLog.ISEvent lastEvent = null;

    boolean warned = false;

    /** EXPERIMENTAL LCM-based ISLog reader.<br>
      * <br>
      * CAVEATS:<br>
      * - The ImageSource API does not provide a mechanism to return a frame
      * and an ImageSourceFormat at the same time. However, ISLogs can contain
      * a different ImageSourceFormat for every image. Therefore, the user must
      * call getFormat() after every successful getFrame() call in order to
      * know the format for image decoding.<br>
      * - The ImageSource API also does not provide a mechanism for returning
      * timestamps, whereas they are actually available in the ISLog event.
      */
    public ImageSourceISLogLCM(String url)
    {
        URLParser up = new URLParser(url);

        // should be an 'islog-lcm'
        String protocol = up.get("protocol");
        assert(protocol.equals("islog-lcm"));

        // the directory of .islog files
        basepath = up.get("network");
        assert(new File(basepath).isDirectory());
        if (!basepath.endsWith("/"))
            basepath = basepath + "/";

        // the lcm channel for url_t messages
        channel = up.get("channel", "ISLOG");

        lcm.subscribe(channel, this);
    }

    public synchronized void messageReceived(LCM lcm, String channel, LCMDataInputStream ins)
    {
        try {
            if (channel.equals(this.channel))
                readImage(new url_t(ins));

        } catch (IOException ex) {
            System.err.println("ImageSourceISLogLCM messageReceived IOException: " + ex);
        } catch (IllegalArgumentException ex) {
            System.err.println("ImageSourceISLogLCM messageReceived IllegalArgumentException: " + ex);
        }
    }

    private void readImage(url_t url) throws IOException, IllegalArgumentException
    {
        URLParser parser = new URLParser(url.url);

        // verify that we're reading the correct log
        ensureLogOpen(parser);

        // get offsets
        String offsetStr = parser.get("offset");
        assert(offsetStr != null);

        // read the frame
        ISLog.ISEvent event = log.readAtPosition(Long.valueOf(offsetStr));
        queue.put(event);
    }

    private void ensureLogOpen(URLParser up) throws IOException
    {
        String protocol = up.get("protocol");
        if (protocol == null)
            throw new IOException();

        String location = up.get("network");
        if (location == null)
            throw new IOException();

        String path = basepath + location;
        // close old log if it's time for the new one
        if (log != null && !path.equals(log.getPath())) {
            log.close();
            log = null;
        }

        // we don't have a log! open one!
        if (log == null) {
            System.out.printf("Opening log at path '%s'\n", path);
            log = new ISLog(path, "r");
        }
    }

    public byte[] getFrame()
    {
        ISLog.ISEvent event = queue.get();

        lastEvent = event;

        return event.buf;
    }

    /** Returns the LAST image's format. Returns null if no image has been read.
      * This is part of the experimental API that will be fixed in future versions.
      */
    public ImageSourceFormat getFormat(int idx)
    {
        assert(idx == 0);

        if (lastEvent == null) {
            if (!warned) {
                warned = true;
                System.out.println("Warning: ImageSourceISLogLCM.getFormat() returning null because no frame has been received");
            }
            return null;
        }

        return lastEvent.ifmt;
    }

    /** Returns the LAST image's timestamp. Returns null if no image has been read.
      * This is part of the experimental API that will be fixed in future versions.
      */
    public Long getTimestamp()
    {
        if (lastEvent == null)
            return null;

        return lastEvent.utime;
    }

    ////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////

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
        System.out.printf(" ImageSourceISLogLCM Info\n");
        System.out.printf("========================================\n");
        System.out.printf("\tBase path: %s\n", basepath);
        System.out.printf("\tChannel: %s\n", channel);
    }

    public int close()
    {
        if (log != null) {
            try {
                log.close();
                return 0;

            } catch (IOException ex) {
                return 1;
            }
        }

        return 0;
    }
}
