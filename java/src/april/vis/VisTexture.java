package april.vis;

import java.awt.*;
import java.awt.image.*;
import java.io.*;

import april.jmat.*;

/**
    In the interest of simplicity, we only attempt to support systems with
    GL_ARB_texture_non_power_of_two. **/
public class VisTexture implements VisSerializable
{
    long id = VisUtil.allocateID();

    int glinternal, glformat, gltype;
    int width, height;
    int bytes_per_pixel;

    static boolean warnedSlowConversion;

    boolean alphaMask;

    int idata[];
    byte bdata[];

    /** You may not subsequently modify im or behavior is undefined. **/
    public VisTexture(BufferedImage input, boolean alphaMask)
    {
        this.alphaMask = alphaMask;

        BufferedImage im = null;

        if (alphaMask) {
            im = VisUtil.coerceImage(input, BufferedImage.TYPE_BYTE_GRAY);
            glinternal = GL.GL_ALPHA8;
            glformat = GL.GL_ALPHA;
            gltype = GL.GL_UNSIGNED_BYTE;
            bytes_per_pixel = 1;

        } else {

            switch (input.getType()) {
                case BufferedImage.TYPE_INT_ARGB: {
                    im = input;
                    glinternal = GL.GL_RGBA8;
                    glformat = GL.GL_BGRA;
                    gltype = GL.GL_UNSIGNED_INT_8_8_8_8_REV;
                    bytes_per_pixel = 4;
                    break;
                }
                case BufferedImage.TYPE_BYTE_GRAY: {
                    im = input;
                    glinternal = GL.GL_LUMINANCE8;
                    glformat = GL.GL_LUMINANCE;
                    gltype = GL.GL_UNSIGNED_BYTE;
                    bytes_per_pixel = 1;
                    break;
                }
                case BufferedImage.TYPE_4BYTE_ABGR: {
                    im = input;
                    glinternal = GL.GL_RGBA8;
                    glformat = GL.GL_ABGR_EXT;
                    gltype = GL.GL_UNSIGNED_INT_8_8_8_8_REV;
                    bytes_per_pixel = 4;
                    break;
                }
                case BufferedImage.TYPE_INT_RGB: {
                    im = input;
                    glinternal = GL.GL_RGB8;
                    glformat = GL.GL_BGRA;
                    gltype = GL.GL_UNSIGNED_INT_8_8_8_8_REV;
                    bytes_per_pixel = 4;
                    break;
                }
                default: {
                    // coerce texture format to a type we know.
                    im = VisUtil.coerceImage(input, BufferedImage.TYPE_INT_ARGB);

                    glinternal = GL.GL_RGBA8;
                    glformat = GL.GL_BGRA;
                    gltype = GL.GL_UNSIGNED_INT_8_8_8_8_REV;
                    bytes_per_pixel = 4;
                    break;
                }
            }
        }

        this.width = input.getWidth();
        this.height = input.getHeight();

        if (im.getRaster().getDataBuffer() instanceof DataBufferInt)
            this.idata = ((DataBufferInt) (im.getRaster().getDataBuffer())).getData();
        else
            this.bdata = ((DataBufferByte) (im.getRaster().getDataBuffer())).getData();
    }

    public void bind(GL gl)
    {
        if (idata != null)
            gl.gldBindTexture(id, glinternal, width, height, glformat, gltype, idata);
        else
            gl.gldBindTexture(id, glinternal, width, height, glformat, gltype, bdata);
    }

    public void unbind(GL gl)
    {
        gl.gldUnbindTexture(id);
    }

    public VisTexture(ObjectReader ins)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeInt(glinternal);
        outs.writeInt(glformat);
        outs.writeInt(gltype);

        outs.writeInt(width);
        outs.writeInt(height);

        outs.writeByte(alphaMask ? 1 : 0);
        outs.writeInts(idata);
        outs.writeBytes(bdata);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        glinternal = ins.readInt();
        glformat = ins.readInt();
        gltype = ins.readInt();

        width = ins.readInt();
        height = ins.readInt();

        alphaMask = (ins.readByte() != 0) ? true : false;
        idata = ins.readInts();
        bdata = ins.readBytes();
    }
}
