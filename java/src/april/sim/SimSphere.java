package april.sim;

import java.awt.*;
import java.io.*;

import april.vis.*;
import april.jmat.*;

public class SimSphere implements SimObject
{
    protected double xyz[];
    protected double r;
    protected Color color;

    public SimSphere(double xyz[], double r, Color c)
    {
        this.xyz = LinAlg.copy(xyz);
        this.r = r;
        this.color = c;
    }

    public SimSphere()
    {
    }

    public double collisionSphere(double p[])
    {
        return Math.max(0, LinAlg.distance(xyz, p) - r);
    }

    public double collisionRay(double p[], double dir[])
    {
        // unit vector from p to center of circle
        double e[] = LinAlg.normalize(LinAlg.subtract(xyz, p));

        // cosine of theta between dir and vector that would lead to
        // center of circle
        double costheta = LinAlg.dotProduct(e, dir);

        // law of cosines gives us:
        // r^2 = x^2 + d^2 - 2xd cos(theta)

        double d = LinAlg.distance(xyz, p);

        // solve for x using quadratic formula
        double A = 1;
        double B = -2*d*costheta;
        double C = d*d - r*r;

        // no collision
        if (B*B - 4*A*C < 0)
            return Double.MAX_VALUE;

        return (-B - Math.sqrt(B*B - 4 * A * C)) / (2*A);
    }

    public void write(BufferedWriter outs) throws IOException
    {
        outs.write(String.format("%f %f %f %d %d %d",
                                 xyz[0], xyz[1], xyz[2],
                                 color.getRed(), color.getGreen(), color.getBlue()));
    }

    public void read(BufferedReader ins) throws IOException
    {
        String line = ins.readLine();
        String toks[] = line.split("\\s+");
        xyz = new double[] { Double.parseDouble(toks[0]),
                             Double.parseDouble(toks[1]),
                             Double.parseDouble(toks[2]) };

        color = new Color(Integer.parseInt(toks[3]),
                          Integer.parseInt(toks[4]),
                          Integer.parseInt(toks[5]));
    }

    public VisObject getVisObject()
    {
        return new VisChain(LinAlg.translate(xyz[0], xyz[1], xyz[2]),
                            new VisSphere(r, color));
    }

    public static void main(String args[])
    {
        SimSphere so = new SimSphere();
        so.xyz = new double[] { 5, 5, 0 };
        so.r = 1;

        System.out.println(so.collisionRay(new double[] { 2, 0, 0 }, LinAlg.normalize(new double[] { 1, 1, 0})));
    }
}
