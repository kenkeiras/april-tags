package april.jcam;

import java.io.*;
import java.util.*;

/** A provider of image data, such as a camera. **/
public abstract class ImageSource
{
    public static ImageSource make(String url) throws IOException
    {
        if (url.startsWith("file:") || url.startsWith("dir:"))
            return new ImageSourceFile(url);

        return new ImageSourceNative(url);
    }

    public static ArrayList<String> getCameraURLs()
    {
        return ImageSourceNative.getCameraURLs();
    }

    public abstract void start();

    public abstract void stop();

    public abstract byte[] getFrame();

    public abstract int getNumFormats();

    public abstract ImageSourceFormat getFormat(int idx);

    /** Try to find a format with the given format string. The first
        such format will be selected.  e.g., format = "GRAY8" or
        "BAYER_RGGB". Returns true on success. **/
    public boolean setFormat(String format)
    {
        for (int i = 0; i < getNumFormats(); i++) {
            ImageSourceFormat ifmt = getFormat(i);
            if (ifmt.format.equals(format)) {
                setFormat(i);
                return true;
            }
        }

        return false;
    }

    public abstract void setFormat(int idx);

    public abstract int getCurrentFormatIndex();

    public ImageSourceFormat getCurrentFormat()
    {
        return getFormat(getCurrentFormatIndex());
    }

    public abstract int setWhiteBalance(int r, int b);

    public abstract int getWhiteBalance(char c);

    public abstract int close();
}
