package april.vis;

import javax.media.opengl.*;
import javax.media.opengl.glu.*;
import java.awt.*;
import april.jmat.geom.*;

import java.util.*;

/** VisObject representing a set of axes. **/
public class VisAxes implements VisObject
{
    double size;

    VisDataLineStyle styles[];

    static ArrayList<double[]> pointsx, pointsy, pointsz;
    static {
        pointsx = new ArrayList<double[]>();
        pointsx.add(new double[] {0, 0, 0});
        pointsx.add(new double[] {1, 0, 0});

        pointsy = new ArrayList<double[]>();
        pointsy.add(new double[] {0, 0, 0});
        pointsy.add(new double[] {0, 1, 0});

        pointsz = new ArrayList<double[]>();
        pointsz.add(new double[] {0, 0, 0});
        pointsz.add(new double[] {0, 0, 1});
    }

    public VisAxes()
    {
        styles = new VisDataLineStyle[] { new VisDataLineStyle(Color.red, 2),
                                          new VisDataLineStyle(Color.green, 2),
                                          new VisDataLineStyle(Color.blue, 2) };

    }

    public void render(VisContext vc, GL gl, GLU glu)
    {
        styles[0].renderStyle(vc, gl, glu, pointsx);

        double ph = 0.2; // height of pyramid
        double pb = 0.2; // size of base.

        gl.glPushMatrix();
        gl.glTranslated(1, 0, 0);
        gl.glRotated(90, 0, 1, 0);
        gl.glScaled(pb, pb, ph);
        GLUtil.pyramidFilled(gl, false);
        gl.glPopMatrix();

        styles[1].renderStyle(vc, gl, glu, pointsy);

        gl.glPushMatrix();
        gl.glTranslated(0, 1, 0);
        gl.glRotated(-90, 1, 0, 0);
        gl.glScaled(pb, pb, ph);
        GLUtil.pyramidFilled(gl, false);
        gl.glPopMatrix();

        styles[2].renderStyle(vc, gl, glu, pointsz);

        gl.glPushMatrix();
        gl.glTranslated(0, 0, 1);
        gl.glScaled(pb, pb, ph);
        GLUtil.pyramidFilled(gl, false);
        gl.glPopMatrix();
    }
}
