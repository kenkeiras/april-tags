package april.vx;

import java.awt.image.*;

public class VxTexture
{
    final long id = VxUtil.allocateID();


    final int width, height;
    final byte[] bbuf;

    final int format; // internal format and format must be the same

    // Do not modify the buffered image after calling this.
    public VxTexture(BufferedImage bim_in)
    {
        // XXX Transparency, but GL_RGBA requires  swapping lots of stuff to get from 4BYTE_ABGR
        BufferedImage bim = VxUtil.convertAndCopyImage(bim_in, BufferedImage.TYPE_3BYTE_BGR);




        bbuf = ((DataBufferByte) (bim.getRaster().getDataBuffer())).getData();
        width = bim.getWidth();
        height = bim.getHeight();

        //Fix color inversion
        swapRB_RGB(bbuf, width, height);

        format = Vx.GL_RGB;
    }


    public static void swapRB_RGB(byte buf[], int width, int height)
    {
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                byte t = buf[y*width*3 + x*3 + 0];
                buf[y*width*3 + x*3 + 0] = buf[y*width*3 + x*3 + 2];
                buf[y*width*3 + x*3 + 2] = t;
            }
    }
}