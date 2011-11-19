package april.vis;

import java.util.*;
import java.awt.*;
import java.io.*;

public class VzLines implements VisObject
{
    VisAbstractVertexData vd;
    int type;
    Style styles[];

    public static final int LINES = 1, LINE_LOOP = 2, LINE_STRIP = 4;

    public static class Style
    {
        Color c;
        double lineWidth;
        VisAbstractColorData cd;

        public Style(Color c, double lineWidth)
        {
            this(new VisConstantColor(c), lineWidth);
        }

        public Style(VisAbstractColorData cd, double lineWidth)
        {
            this.cd = cd;
            this.lineWidth = lineWidth;
        }

        public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl, VzLines vlines)
        {
            vlines.vd.bindVertex(gl);
            cd.bindColor(gl);

            gl.glNormal3f(0, 0, 1);
            gl.glLineWidth((float) lineWidth);

            if (vlines.type == LINES)
                gl.glDrawArrays(GL.GL_LINES, 0, vlines.vd.size());
            else if (vlines.type == LINE_STRIP)
                gl.glDrawArrays(GL.GL_LINE_STRIP, 0, vlines.vd.size());
            else if (vlines.type == LINE_LOOP)
                gl.glDrawArrays(GL.GL_LINE_LOOP, 0, vlines.vd.size());

            cd.unbindColor(gl);
            vlines.vd.unbindVertex(gl);
        }
    }

    public VzLines(VisAbstractVertexData vd, int type, Style ... styles)
    {
        this.vd = vd;
        this.type = type;
        this.styles = styles;
    }

    public synchronized void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        for (Style style : styles)
            style.render(vc, layer, rinfo, gl, this);
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeObject(vd);
        outs.writeInt(type);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        vd = (VisAbstractVertexData) ins.readObject();
        this.type = ins.readInt();
    }
}
