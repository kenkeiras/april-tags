package april.vis;

import java.awt.*;

public class VzCamera implements VisObject
{
    static Color defaultFill = Color.gray;

    Color color;

    public VzCamera()
    {
        this(defaultFill);
    }

    public VzCamera(Color fill)
    {
        this.color = fill;
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        double s = 0.25;
        gl.glColor(color);

        gl.glPushMatrix();
        gl.glScaled(s, s, s);
        gl.glTranslated(0, 0, -1.0);
        VzSquarePyramid vp = new VzSquarePyramid(new VisFillStyle(color), true);
        vp.render(vc, layer, rinfo, gl);
        gl.glPopMatrix();

        gl.glPushMatrix();
        gl.glScaled(s, s, s);
        gl.glTranslated(0, 0, 0.25);
        VzBox vb = new VzBox(s, s, s, new VisFillStyle(color));
        vb.render(vc, layer, rinfo, gl);
        gl.glPopMatrix();
/*
        gl.glBegin(GL.GL_LINES);
        gl.glVertex3d(0, -.2, 0);
        gl.glVertex3d(0, .4, 0);

        for (int i = 0; i < 3; i++) {
            gl.glVertex3d(0, .4, 0);
            double theta = i*2.0*Math.PI/3;
            gl.glVertex3d(.05*Math.sin(theta), .3, .05*Math.cos(theta));
        }

        gl.glEnd();
*/
    }

}
