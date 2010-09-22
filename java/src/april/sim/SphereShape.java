package april.sim;

import java.awt.*;
import java.io.*;

import april.vis.*;
import april.jmat.*;

public class SphereShape implements Shape
{
    protected double r;

    public SphereShape(double r)
    {
        this.r = r;
    }

    public boolean collision(Shape _s, double T[][])
    {
        return false;
    }

    public double collisionRay(double p[], double dir[])
    {
        // unit vector from p to center of circle
        double e[] = p;

        // cosine of theta between dir and vector that would lead to
        // center of circle
        double costheta = LinAlg.dotProduct(e, dir);

        // law of cosines gives us:
        // r^2 = x^2 + d^2 - 2xd cos(theta)

        double d = LinAlg.magnitude(p);

        // solve for x using quadratic formula
        double A = 1;
        double B = -2*d*costheta;
        double C = d*d - r*r;

        // no collision
        if (B*B - 4*A*C < 0)
            return Double.MAX_VALUE;

        return (-B - Math.sqrt(B*B - 4 * A * C)) / (2*A);
    }
}
