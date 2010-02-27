package april.vis;

import javax.media.opengl.*;
import javax.media.opengl.glu.*;
import java.awt.*;
import april.jmat.geom.*;

import java.util.*;

/** VisObject representing a robot. **/
public class VisRobot implements VisObject
{
    public Color color = new Color(40, 40, 100);
    public Color outlineColor = new Color(40, 40, 255);

    int  selectedState; // 0 = none, 1 = hover, 2 = select

    public VisRobot()
    {
    }

    public VisRobot(Color color, Color outlineColor)
    {
        this.color = color;
        this.outlineColor = outlineColor;
    }

    public VisRobot(Color color)
    {
        this.color = color;
        this.outlineColor = color.brighter();
    }

    public void setSelectedState(int v)
    {
        this.selectedState = v;
    }

    public void render(VisContext vc, GL gl, GLU glu)
    {
        double length = 0.6;
        double width = .35;

        if (selectedState == 1) {
            VisUtil.setColor(gl, VisUtil.hoverColor);
        } else if (selectedState == 2) {
            VisUtil.setColor(gl, VisUtil.pickColor);
        }

        if (selectedState > 0) {
            gl.glBegin(gl.GL_QUADS);
            gl.glVertex2d(-length, -width);
            gl.glVertex2d(-length, width);
            gl.glVertex2d(length, width);
            gl.glVertex2d(length, -width);
            gl.glEnd();
        }

        gl.glLineWidth(1f);

        VisUtil.setColor(gl, color);
        gl.glBegin(gl.GL_TRIANGLES);
        gl.glVertex2d(-length/2, width/2);
        gl.glVertex2d(length/2, 0);
        gl.glVertex2d(-length/2, -width/2);
        gl.glEnd();

        VisUtil.setColor(gl, outlineColor);
        gl.glBegin(gl.GL_LINE_LOOP);
        gl.glVertex2d(-length/2, width/2);
        gl.glVertex2d(length/2, 0);
        gl.glVertex2d(-length/2, -width/2);
        gl.glEnd();
    }
}
