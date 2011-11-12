package april.vis;

import java.util.*;
import java.awt.*;
import java.io.*;

public class VisPoints implements VisObject, VisSerializable
{
    VisAbstractVertexData vd;
    VisAbstractColorData cd;
    double pointSize;

    public VisPoints(VisAbstractVertexData vd, VisAbstractColorData cd, double pointSize)
    {
        this.vd = vd;
        this.cd = cd;
        this.pointSize = pointSize;
    }

    public synchronized void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        vd.bindVertex(gl);
        cd.bindColor(gl);

        gl.glNormal3f(0, 0, 1);

        gl.glPointSize((float) pointSize);
        gl.glDrawArrays(GL.GL_POINTS, 0, vd.size());

        cd.unbindColor(gl);
        vd.unbindVertex(gl);
    }

    public VisPoints(ObjectReader ins)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeObject(vd);
        outs.writeObject(cd);
        outs.writeDouble(pointSize);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        vd = (VisAbstractVertexData) ins.readObject();
        cd = (VisAbstractColorData) ins.readObject();
        pointSize = ins.readDouble();
    }
}
