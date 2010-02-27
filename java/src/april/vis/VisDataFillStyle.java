package april.vis;

import java.awt.*;
import java.util.*;
import javax.media.opengl.*;
import javax.media.opengl.glu.*;

import april.jmat.*;
import april.jmat.geom.*;

/** Treat VisData points as a polygon and fills the interior. **/
public class VisDataFillStyle implements VisDataStyle
{
    Color c;
    static boolean warned = false;
    Colorizer colorizer;

    public VisDataFillStyle(Colorizer colorizer)
    {
        this.colorizer = colorizer;
    }

    public VisDataFillStyle(Color c)
    {
        this.c = c;
    }

    public void renderStyle(VisContext vc, GL gl, GLU glu, ArrayList<double[]> points)
    {
        if (c != null)
            VisUtil.setColor(gl, c);

        april.jmat.geom.Polygon p = new april.jmat.geom.Polygon(points);

        gl.glBegin(GL.GL_TRIANGLES);

        for (int triangle[] : p.getTriangles()) {
            for (int i = 0; i < 3; i++) {
                double vertex[] = p.getPoint(triangle[i]);

                if (colorizer != null)
                    VisUtil.setColor(gl, colorizer.colorize(vertex));

                if (vertex.length == 2)
                    gl.glVertex2dv(vertex, 0);
                else
                    gl.glVertex3dv(vertex, 0);
            }
        }

        gl.glEnd();
    }
}

