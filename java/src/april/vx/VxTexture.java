package april.vx;

import java.awt.image.*;

public class VxTexture
{
    final VxResource vr;
    final int width, height;

    final int format; // internal format and format must be the same

    // Do not modify the buffered image after calling this.
    public VxTexture(BufferedImage bim_in)
    {
        // XXX Transparency, but GL_RGBA requires  swapping lots of stuff to get from 4BYTE_ABGR
        BufferedImage bim = VxUtil.convertAndCopyImage(bim_in, BufferedImage.TYPE_3BYTE_BGR);

        width = bim.getWidth();
        height = bim.getHeight();
        format = Vx.GL_RGB;

        byte bbuf[] = ((DataBufferByte) (bim.getRaster().getDataBuffer())).getData();
        //Fix color inversion
        swapRB_RGB(bbuf, width, height);
        vr = new VxResource(Vx.GL_UNSIGNED_BYTE, bbuf, bbuf.length, 1, VxUtil.allocateID());
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