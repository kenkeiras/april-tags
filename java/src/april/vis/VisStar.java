package april.vis;

import javax.media.opengl.*;
import javax.media.opengl.glu.*;
import java.awt.*;
import april.jmat.geom.*;

import java.util.*;

/** VisObject that draws a star. **/
public class VisStar implements VisObject
{
    //    Color  c = new Color(220, 220, 0);
    Color c;
    Color outlineColor = new Color(100, 100, 0);

    int  selectedState; // 0 = none, 1 = hover, 2 = select

    double vertices[][];
    double size = 0.8;
    double ratio = 0.4;

    public VisStar()
    {
        this(new Color(255,255,0), 5);
    }

    public VisStar(Color c)
    {
        this(c, 5);
    }

    public VisStar(Color c, int npoints)
    {
        this.c = c;
        vertices = new double[npoints*2][2];

        for (int i = 0; i < vertices.length; i++) {
            double r = (i&1)==0 ? size : size*ratio;
            double theta = 2*Math.PI * i / (vertices.length );

            vertices[i][0] = Math.cos(theta)*r;
            vertices[i][1] = Math.sin(theta)*r;
        }
    }

    public void render(VisContext vc, GL gl, GLU glu)
    {
        gl.glLineWidth(1f);

        VisUtil.setColor(gl, c);
        gl.glBegin(gl.GL_TRIANGLE_FAN);
        gl.glVertex2d(0, 0);
        for (int i = 0; i < vertices.length; i++) {
            gl.glVertex2d(vertices[i][0], vertices[i][1]);
        }
        gl.glVertex2d(vertices[0][0], vertices[0][1]);
        gl.glEnd();

        VisUtil.setColor(gl, outlineColor);
        gl.glBegin(gl.GL_LINE_LOOP);
        for (int i = 0; i < vertices.length; i++) {
            gl.glVertex2d(vertices[i][0], vertices[i][1]);
        }
        gl.glEnd();
    }
}
