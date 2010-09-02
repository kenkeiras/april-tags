package april.vis;

import java.awt.*;
import java.util.*;
import javax.media.opengl.*;
import javax.media.opengl.glu.*;

import april.jmat.geom.*;

import lcm.lcm.*;
import java.io.*;

/** Render VisData as a connected line. **/
public class VisDataLineStyle implements VisDataStyle, VisSerializable
{
    Color c;
    float width;
    boolean loop;

    public VisDataLineStyle(Color c, double width, boolean loop)
    {
        this.c = c;
        this.width = (float) width;
        this.loop = loop;
    }

    public VisDataLineStyle(Color c, double width)
    {
        this(c, width, false);
    }

    public void renderStyle(VisContext vc, GL gl, GLU glu, VisData vdata)
    {
        ArrayList<double[]> points = vdata.points;

        VisUtil.setColor(gl, c);
        gl.glLineWidth(width);

        gl.glDisable(GL.GL_LIGHTING);
        gl.glBegin( loop ? gl.GL_LINE_LOOP : gl.GL_LINE_STRIP );

        for (double p[] : points) {
            if (p.length >= 3)
                gl.glVertex3dv(p, 0);
            else
                gl.glVertex2dv(p, 0);
        }

        gl.glEnd();
        gl.glEnable(GL.GL_LIGHTING);
    }

    public VisDataLineStyle()
    {
    }

    public void serialize(LCMDataOutputStream out) throws IOException
    {
        out.writeInt(c.getRGB());
        out.writeFloat(width);
        out.writeBoolean(loop);
    }

    public void unserialize(LCMDataInputStream in) throws IOException
    {
        c = new Color(in.readInt(), true);
        width = in.readFloat();
        loop = in.readBoolean();
    }
}
