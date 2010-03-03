package april.lcm;

import april.lcmtypes.*;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.*;

public class image_t_util
{
    public static final int FORMAT_JPEG = 1196444237;
    public static final int FORMAT_RGB = 859981650;

    public static BufferedImage decode(image_t v) throws IOException
    {
        switch (v.pixelformat) {

            case FORMAT_JPEG: // JPEG
                return ImageIO.read(new ByteArrayInputStream(v.image));

            case FORMAT_RGB:  // raw RGB
                return decodeRGB(v);

            default:        // uncompressed gray scale.
                return decodeRAW(v);
        }
    }

    /** Quality: 0 = low, 1 = high **/
    public static image_t encodeJPEG(BufferedImage bi, float quality) throws IOException
    {
        image_t v = new image_t();
        v.width = (short) bi.getWidth();
        v.height = (short) bi.getHeight();
        v.stride = v.width; // unused
        v.pixelformat = FORMAT_JPEG;

        Iterator<ImageWriter> iter = ImageIO.getImageWritersByMIMEType("image/jpeg");
        ImageWriter writer = iter.next();

        ImageWriteParam params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(quality);

        ByteArrayOutputStream bouts = new ByteArrayOutputStream();

        try {
            writer.setOutput(new MemoryCacheImageOutputStream(bouts));
            writer.write(null, new IIOImage(bi, null, null), params);
        } catch (IOException ex) {
            System.out.println("WRN: "+ex);
            return null;
        }

        v.image = bouts.toByteArray();
        v.size = v.image.length;

        return v;
    }

    static final int grayToRGB(byte v)
    {
        int g = v&0xff;
        return (g<<16)|(g<<8)|g;
    }

    static BufferedImage decodeRAW(image_t v)
    {
        BufferedImage bi = new BufferedImage(v.width, v.height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < v.height; y++) {
            for (int x = 0; x < v.width; x++) {
                bi.setRGB(x, y, grayToRGB(v.image[x+y*v.stride]));
            }
        }

        return bi;
    }

    static BufferedImage decodeRGB(image_t v)
    {
        BufferedImage bi = new BufferedImage(v.width, v.height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < v.height; y++) {
            for (int x = 0; x < v.width; x++) {
                int index = 3*x+y*v.stride;
                byte r = v.image[index +0];
                byte g = v.image[index +1];
                byte b = v.image[index +2];


                int r_int = r & 0xff;
                int g_int = g & 0xff;
                int b_int = b & 0xff;

                Color col = new Color(r_int,g_int,b_int);
                bi.setRGB(x, y, col.getRGB());
            }
        }

        return bi;
    }
}
