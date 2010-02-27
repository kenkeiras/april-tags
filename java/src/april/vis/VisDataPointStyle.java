package april.vis;

import java.awt.*;
import java.util.*;
import javax.media.opengl.*;
import javax.media.opengl.glu.*;

import april.jmat.geom.*;

/** Render VisData as individual points. **/
public class VisDataPointStyle implements VisDataStyle
{
    Colorizer colorizer;
    Color c;
    float size;

    public VisDataPointStyle(Colorizer colorizer, double size)
    {
        this.colorizer = colorizer;
        this.size = (float) size;
    }

    public VisDataPointStyle(Color c, double size)
    {
        this.c = c;
        this.size = (float) size;
    }

    public void renderStyle(VisContext vc, GL gl, GLU glu, ArrayList<double[]> points)
    {
        if (c != null)
            VisUtil.setColor(gl, c);

        gl.glPointSize(size);

        gl.glDisable(GL.GL_LIGHTING);
        gl.glBegin(gl.GL_POINTS);
        for (double p[] : points) {
            if (colorizer != null)
                VisUtil.setColor(gl, colorizer.colorize(p));

            if (p.length >= 3)
                gl.glVertex3dv(p, 0);
            else
                gl.glVertex2dv(p, 0);
        }
        gl.glEnd();
        gl.glEnable(GL.GL_LIGHTING);
    }
}
