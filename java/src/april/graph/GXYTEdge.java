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
public class GXYTEdge extends GEdge
{
    public double z[]; // x, y, theta
    public double truth[];

    public double P[][]; // covariance

    double W[][];  // inverse(P)

    MultiGaussian mg;

    public GXYTEdge()
    {
    }

    public GXYTEdge copy()
    {
        GXYTEdge e = new GXYTEdge();
        e.nodes = LinAlg.copy(nodes);
        e.z = LinAlg.copy(z);
        if (truth != null)
            e.truth = LinAlg.copy(truth);
        e.P = LinAlg.copy(P);

        return e;
    }

    public GXYTEdge invert()
    {
        GXYTEdge ge = new GXYTEdge();
        ge.nodes = new int[] { nodes[1], nodes[0] };

        double x = z[0], y = z[1], theta = z[2];
        double s = Math.sin(theta), c = Math.cos(theta);

        ge.z = LinAlg.xytInverse(z);

        double J[][] = new double[][] { { -c, -s, -c*y + s*x },
                                        { s,  -c,  s*y + c*x },
                                        { 0,   0,     -1     } };
        ge.P = LinAlg.matrixABCt(J, P, J);
        return ge;
    }

    public double[][] getW()
    {
        if (W == null)
            W = LinAlg.inverse(P);
        return W;
    }

    public double getChi2(Graph g)
    {
        GXYTNode gna = (GXYTNode) g.nodes.get(nodes[0]);
        GXYTNode gnb = (GXYTNode) g.nodes.get(nodes[1]);

        double zpred[] = LinAlg.xytInvMul31(gna.state, gnb.state);
        getMultiGaussian();
        zpred[2] = MathUtil.mod2pi(mg.getMean()[2], z[2]);

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
        return 3;
    }

    public void write(StructureWriter outs) throws IOException
    {
        outs.writeComment("a, b");
        outs.writeInt(nodes[0]);
        outs.writeInt(nodes[1]);

        outs.writeComment("XYT");
        outs.writeDoubles(z);
        outs.writeComment("XYT truth");
        outs.writeDoubles(truth);
        outs.writeComment("Covariance");
        outs.writeMatrix(P);
    }

    public void read(StructureReader ins) throws IOException
    {
        nodes = new int[2];
        nodes[0] = ins.readInt();
        nodes[1] = ins.readInt();

        z = ins.readDoubles();
        truth = ins.readDoubles();
        P = ins.readMatrix();
    }

    public Linearization linearize(Graph g, Linearization lin)
    {
        if (lin == null) {
            lin = new Linearization();
            lin.J.add(new double[3][3]);
            lin.J.add(new double[3][3]);
        }

        GXYTNode gna = (GXYTNode) g.nodes.get(nodes[0]);
        GXYTNode gnb = (GXYTNode) g.nodes.get(nodes[1]);

        double xa = gna.state[0], ya = gna.state[1], ta = gna.state[2];
        double xb = gnb.state[0], yb = gnb.state[1], tb = gnb.state[2];
        double sa = Math.sin(ta), ca = Math.cos(ta);

        // Jacobian of the constraint WRT state a
        if (true) {
            double J[][] = lin.J.get(0);
            J[0][0] = -ca;
            J[0][1] = -sa;
            J[0][2] = -sa*(xb-xa)+ca*(yb-ya);
            J[1][0] = sa;
            J[1][1] = -ca;
            J[1][2] = -ca*(xb-xa)-sa*(yb-ya);
            J[2][2] = -1;
        }

        // Jacobian of the constraint WRT state b
        if (true) {
            double J[][] = lin.J.get(1);
            J[0][0] = ca;
            J[0][1] = sa;
            J[1][0] = -sa;
            J[1][1] = ca;
            J[2][2] = 1;
        }

        // compute the residual
        lin.R = LinAlg.xytInvMul31(new double[] {xa, ya, ta},
                                   new double[] {xb, yb, tb});
        LinAlg.minusEquals(lin.R, z);
        lin.R[2] = MathUtil.mod2pi(lin.R[2]);

        lin.W = getW();
        return lin;
    }
}
