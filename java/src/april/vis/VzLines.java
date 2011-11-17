package april.vis;

import java.util.*;
import java.awt.*;
import java.io.*;

public class VzLines implements VisObject
{
    VisAbstractVertexData vd;
    VisAbstractColorData cd;
    double lineWidth;

    public enum TYPE { LINES, LINE_LOOP, LINE_STRIP };

    TYPE type;

    public VzLines(VisAbstractVertexData vd, VisAbstractColorData cd, double lineWidth, TYPE type)
    {
        this.vd = vd;
        this.cd = cd;
        this.lineWidth = lineWidth;
        this.type = type;
    }

    public synchronized void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        vd.bindVertex(gl);
        cd.bindColor(gl);

        gl.glNormal3f(0, 0, 1);
        gl.glLineWidth((float) lineWidth);

        if (type == TYPE.LINES)
            gl.glDrawArrays(GL.GL_LINES, 0, vd.size());
        else if (type == TYPE.LINE_STRIP)
            gl.glDrawArrays(GL.GL_LINE_STRIP, 0, vd.size());
        else if (type == TYPE.LINE_LOOP)
            gl.glDrawArrays(GL.GL_LINE_LOOP, 0, vd.size());

        cd.unbindColor(gl);
        vd.unbindVertex(gl);
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeObject(vd);
        outs.writeObject(cd);
        outs.writeDouble(lineWidth);
        outs.writeUTF(type.name());
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        vd = (VisAbstractVertexData) ins.readObject();
        cd = (VisAbstractColorData) ins.readObject();
        lineWidth = ins.readDouble();
        this.type = TYPE.valueOf(ins.readUTF());
    }
}
