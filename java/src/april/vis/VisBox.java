package april.vis;

import java.awt.*;

import javax.media.opengl.*;
import javax.media.opengl.glu.*;

import april.jmat.geom.*;

import java.util.*;

/** A simple 3D box. **/
public class VisBox implements VisObject
{
    double cx, cy, cz;
    double sizex, sizey, sizez;
    Color  fillcolor;
    Color  linecolor;

    public VisBox(double sizex, double sizey, double sizez, Color fillcolor)
    {
        this(0, 0, 0, sizex, sizey, sizez, fillcolor, null);
    }

    public VisBox(double sizex, double sizey, double sizez, VisDataFillStyle style)
    {
        this(0, 0, 0, sizex, sizey, sizez, style.c, null);
    }

    public VisBox(double cx, double cy, double cz, double sizex, double sizey, double sizez, Color fillcolor)
    {
        this(cx, cy, cz, sizex, sizey, sizez, fillcolor, null);
    }

    public VisBox(double cx, double cy, double cz, double sizex, double sizey, double sizez, Color fillcolor, Color linecolor)
    {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.sizex = sizex;
        this.sizey = sizey;
        this.sizez = sizez;
        this.fillcolor = fillcolor;
        this.linecolor = linecolor;
    }

    public void render(VisContext vc, GL gl, GLU glu)
    {
        gl.glTranslated(cx, cy, cz);
        gl.glScaled(sizex, sizey, sizez);

        if (fillcolor != null) {
            VisUtil.setColor(gl, fillcolor);
            GLUtil.cubeFilled(gl);
        }

        if (linecolor != null) {
            VisUtil.setColor(gl, linecolor);
            GLUtil.cubeLines(gl);
        }
    }
}
