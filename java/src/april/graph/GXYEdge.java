package april.graph;

import java.io.*;

import april.jmat.*;
import april.util.*;

/**  AT = B, where A and B are poses, and T is the constraint measured
 *  by this edge.
 *
 * Observation model is thus: T = inv(A)*B
 *
 **/
public class GXYEdge extends GEdge
{
    public double z[]; // x, y, theta
    public double truth[];

    public double P[][]; // covariance

    double W[][];  // inverse(P)

    MultiGaussian mg;

    public GXYEdge()
    {
    }

    public GXYEdge copy()
    {
        GXYEdge e = new GXYEdge();
        e.a = a;
        e.b = b;
        e.z = LinAlg.copy(z);
        if (truth != null)
            e.truth = LinAlg.copy(truth);
        e.P = LinAlg.copy(P);

        return e;
    }

    public double[][] getW()
    {
        if (W == null)
            W = LinAlg.inverse(P);
        return W;
    }

    public double getChi2(Graph g)
    {
        GNode gna = g.nodes.get(a);
        GNode gnb = g.nodes.get(b);

        double xa = gna.state[0], ya = gna.state[1], ta = 0;
        double xb = gnb.state[0], yb = gnb.state[1], tb = 0;
        double sa = Math.sin(ta), ca = Math.cos(ta);

        if (gna instanceof GXYTNode)
            ta = gna.state[2];


        double zpred[] = LinAlg.resize(LinAlg.xytInvMul31(new double[] {xa, ya, ta},
                                                          new double[] {xb, yb, tb}),2);
        getMultiGaussian();

        return mg.chi2(zpred);
    }

    public MultiGaussian getMultiGaussian()
    {
        if (mg == null)
            mg = new MultiGaussian(P, z);
        return mg;
    }

    public int getDOF()
    {
        return 2;
    }

    public void write(StructureWriter outs) throws IOException
    {
        outs.writeComment("a, b");
        outs.writeInt(a);
        outs.writeInt(b);

        outs.writeComment("XY");
        outs.writeDoubles(z);
        outs.writeComment("XY truth");
        outs.writeDoubles(truth);
        outs.writeComment("Covariance");
        outs.writeMatrix(P);
    }

    public void read(StructureReader ins) throws IOException
    {
        a = ins.readInt();
        b = ins.readInt();
        z = ins.readDoubles();
        truth = ins.readDoubles();
        P = ins.readMatrix();
    }

    public Linearization linearize(Graph g, Linearization lin)
    {
        if (lin == null) {
            lin = new Linearization();
            lin.Ja = new double[2][3];
            lin.Jb = new double[2][3];
        }

        GNode gna = g.nodes.get(a);
        GNode gnb = g.nodes.get(b);

        double xa = gna.state[0], ya = gna.state[1], ta = 0;
        double xb = gnb.state[0], yb = gnb.state[1], tb = 0;
        double sa = Math.sin(ta), ca = Math.cos(ta);

        if (gna instanceof GXYTNode)
            ta = gna.state[2];

        // Jacobian of the constraint WRT state a
        lin.Ja[0][0] = -ca;
        lin.Ja[0][1] = -sa;
        lin.Ja[0][2] = -sa*(xb-xa)+ca*(yb-ya);
        lin.Ja[1][0] = sa;
        lin.Ja[1][1] = -ca;
        lin.Ja[1][2] = -ca*(xb-xa)-sa*(yb-ya);

        // Jacobian of the constraint WRT state b
        lin.Jb[0][0] = ca;
        lin.Jb[0][1] = sa;
        lin.Jb[1][0] = -sa;
        lin.Jb[1][1] = ca;

        // compute the residual
        lin.R = LinAlg.resize(LinAlg.xytInvMul31(new double[] {xa, ya, ta},
                                                 new double[] {xb, yb, tb}),2);

        LinAlg.minusEquals(lin.R, z);

        lin.W = getW();
        return lin;
    }
}
