package april.sim;

import java.awt.*;
import java.io.*;

import april.vis.*;
import april.jmat.*;

public class SimBox implements SimObject
{
    public double xyz[];  // position
    public double t;      // orientation
    public double sxyz[]; // size
    public Color color = Color.gray;

    public SimBox(double xyz[], double t, double sxyz[], Color c)
    {
        this.xyz = LinAlg.copy(xyz);
        this.t = t;
        this.sxyz = LinAlg.copy(sxyz);
        this.color = c;
    }

    public SimBox()
    {
    }

    public double collisionSphere(double p[])
    {
        double dx = p[0] - xyz[0];
        double dy = p[1] - xyz[1];
        double dz = p[2] - xyz[2];

        // handle rotation
        double px = dx*Math.cos(-t) - dy*Math.sin(-t);
        double py = dx*Math.sin(-t) + dy*Math.cos(-t);
        double pz = dz;

        // how far away are we?
        double ex = Math.max(0, Math.abs(px) - sxyz[0]/2);
        double ey = Math.max(0, Math.abs(py) - sxyz[1]/2);
        double ez = Math.max(0, Math.abs(pz) - sxyz[2]/2);

        double e2 = ex*ex + ey*ey + ez*ez;

        return Math.sqrt(e2);
    }

    public double collisionRay(double p[], double dir[])
    {
        if (t != 0) {
            double R[][] = LinAlg.rotateZ(-t);
            p = LinAlg.transform(R, p);
            dir = LinAlg.transform(R, dir);
        }

        return LinAlg.rayCollisionBox(xyz, dir, sxyz);
    }

    public void write(BufferedWriter outs) throws IOException
    {
        outs.write(String.format("%f %f %f %f %f %f %f %d %d %d\n",
                                 xyz[0], xyz[1], xyz[2], t, sxyz[0], sxyz[1], sxyz[2],
                                 color.getRed(), color.getGreen(), color.getBlue()));
    }

    public void read(BufferedReader ins) throws IOException
    {
        String line = ins.readLine();
        String toks[] = line.split("\\s+");
        xyz = new double[] { Double.parseDouble(toks[0]),
                             Double.parseDouble(toks[1]),
                             Double.parseDouble(toks[2]) };

        t = Double.parseDouble(toks[3]);

        sxyz = new double[] { Double.parseDouble(toks[4]),
                              Double.parseDouble(toks[5]),
                              Double.parseDouble(toks[6]) };

        color = new Color(Integer.parseInt(toks[7]),
                          Integer.parseInt(toks[8]),
                          Integer.parseInt(toks[9]));
    }

    public VisObject getVisObject()
    {
        return new VisChain(LinAlg.translate(xyz[0], xyz[1], xyz[2]),
                            LinAlg.rotateZ(t),
                            new VisBox(sxyz[0], sxyz[1], sxyz[2], color));
    }
}