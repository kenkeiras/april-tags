package april.vx;

import java.awt.image.*;

public class VxTexture
{
    final long id = VxUtil.allocateID();


    final int width, height;
    final byte[] bbuf;

    final int format; // internal format and format must be the same

    // Do not modify the buffered image after calling this.
    public VxTexture(BufferedImage bim)
    {
        assert(bim.getType() == BufferedImage.TYPE_3BYTE_BGR);// best chance of rendering correctly

        //XXX Colors will be inverted

        bbuf = ((DataBufferByte) (bim.getRaster().getDataBuffer())).getData();
        width = bim.getWidth();
        height = bim.getHeight();


        format = Vx.GL_RGB;
    }

}