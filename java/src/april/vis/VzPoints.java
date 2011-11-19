package april.vis;

import java.util.*;
import java.awt.*;
import java.io.*;

public class VzPoints implements VisObject, VisSerializable
{
    VisAbstractVertexData vd;
    Style styles[];

    public static class Style
    {
        Color c;
        double pointSize;
        VisAbstractColorData cd;

        public Style(Color c, double pointSize)
        {
            this(new VisConstantColor(c), pointSize);
        }

        public Style(VisAbstractColorData cd, double pointSize)
        {
            this.cd = cd;
            this.pointSize = pointSize;
        }

        public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl, VzPoints vpoints)
        {
            vpoints.vd.bindVertex(gl);
            cd.bindColor(gl);

            gl.glNormal3f(0, 0, 1);
            gl.glPointSize((float) pointSize);

            gl.glDrawArrays(GL.GL_POINTS, 0, vpoints.vd.size());

            cd.unbindColor(gl);
            vpoints.vd.unbindVertex(gl);
        }
    }

    public VzPoints(VisAbstractVertexData vd, Style ... styles)
    {
        this.vd = vd;
        this.styles = styles;
    }

    public synchronized void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        for (Style style : styles)
            style.render(vc, layer, rinfo, gl, this);
    }

    public VzPoints(ObjectReader ins)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeObject(vd);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        vd = (VisAbstractVertexData) ins.readObject();
    }
}
