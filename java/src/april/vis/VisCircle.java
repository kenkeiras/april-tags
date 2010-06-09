package april.vis;

import javax.media.opengl.*;
import javax.media.opengl.glu.*;
import java.awt.*;
import java.util.*;

import april.jmat.*;
import april.jmat.geom.*;

public class VisCircle extends VisData
{
    double radius;
    double theta0 = 0;
    double theta1 = 2*Math.PI;
    int npoints = 100;

    public VisCircle(double radius, VisDataStyle ... styles)
    {
        this.radius = radius;
        for (VisDataStyle style : styles)
            this.styles.add(style);
    }

    /** must be called before the object is rendered. **/
    public void setThetaRange(double theta0, double theta1)
    {
        this.theta0 = theta0;
        this.theta1 = theta1;
    }

    public void render(VisContext vc, GL gl, GLU glu)
    {
        if (points.size() == 0) {

            assert(theta0 <= theta1);
            for (double t = theta0; t <= theta1; t+= (2*Math.PI/npoints)) {
                points.add(new double[] { radius * Math.cos(t),
                                          radius * Math.sin(t) });
            }
        }

        for (VisDataStyle style: styles)
            style.renderStyle(vc, gl, glu, this);
    }
}
