package april.camera.calibrator;

import java.io.*;
import java.util.*;

import april.camera.*;
import april.graph.*;
import april.jmat.*;
import april.util.*;

public class GTagEdge extends GEdge
{
    //public static long time_linearize = 0;
    //public static long time_residual = 0;
    //public static long time_jacobian_intrinsics = 0;
    //public static long time_jacobian_extrinsics = 0;
    //public static long time_jacobian_mosaics = 0;
    //public static long time_number_of_linearizations = 0;

    // inherited: nodes
    ArrayList<double[]> pixel_observations = new ArrayList<double[]>();
    ArrayList<double[]> mosaic_coordinates = new ArrayList<double[]>();

    boolean hasCameraExtrinsics = true;

    // indices in this.nodes[] for GNode objects
    int CI = 0;
    int CE = 1;
    int ME = 2;

    public GTagEdge()
    {
    }

    // For the "privileged" camera (at the origin)
    public GTagEdge(int calNodeIndex, int mosaicExtrinsicsIndex,
                    ArrayList<double[]> xys_px,
                    ArrayList<double[]> xyzs_m)
    {
        this(calNodeIndex, -1, mosaicExtrinsicsIndex, xys_px, xyzs_m);
    }

    public GTagEdge(int calNodeIndex, int calExtrisicsIndex, int mosaicExtrinsicsIndex,
                    ArrayList<double[]> xys_px,
                    ArrayList<double[]> xyzs_m)
    {
        if (calExtrisicsIndex == -1) {
            hasCameraExtrinsics = false;
            CI = 0;
            CE = -1;
            ME = 1;
        }

        if (hasCameraExtrinsics)
            this.nodes = new int[] {calNodeIndex, calExtrisicsIndex, mosaicExtrinsicsIndex};
        else
            this.nodes = new int[] {calNodeIndex, mosaicExtrinsicsIndex};

        assert(xys_px.size() == xyzs_m.size());

        for (double[] xy_px : xys_px)
            this.pixel_observations.add(LinAlg.copy(xy_px));

        for (double[] xyz_m : xyzs_m)
            this.mosaic_coordinates.add(LinAlg.copy(xyz_m));
    }

    public int getDOF()
    {
        return pixel_observations.size()*2;
    }

    public double[] getResidualExternal(Graph g)
    {
        assert(g.nodes.get(this.nodes[CI]) instanceof GIntrinsicsNode);
        if (hasCameraExtrinsics)
            assert(g.nodes.get(this.nodes[CE]) instanceof GExtrinsicsNode);
        assert(g.nodes.get(this.nodes[ME]) instanceof GExtrinsicsNode);

        GIntrinsicsNode cameraIntrinsics = (GIntrinsicsNode) g.nodes.get(this.nodes[CI]);
        GExtrinsicsNode cameraExtrinsics = null;
        if (hasCameraExtrinsics)
            cameraExtrinsics = (GExtrinsicsNode) g.nodes.get(this.nodes[CE]);
        GExtrinsicsNode mosaicExtrinsics = (GExtrinsicsNode) g.nodes.get(this.nodes[ME]);

        return getResidual(cameraIntrinsics, cameraExtrinsics, mosaicExtrinsics);
    }

    private double[] getResidual(GIntrinsicsNode cameraIntrinsics,
                                 GExtrinsicsNode cameraExtrinsics,
                                 GExtrinsicsNode mosaicExtrinsics)
    {
        cameraIntrinsics.updateIntrinsics();
        double[][] cameraToGlobal = LinAlg.identity(4);
        if (hasCameraExtrinsics)
            cameraToGlobal = cameraExtrinsics.getMatrix();
        double[][] mosaicToGlobal = mosaicExtrinsics.getMatrix();

        double[][] mosaicToCamera = LinAlg.matrixAB(LinAlg.inverse(cameraToGlobal),
                                                    mosaicToGlobal);

        double residual[] = new double[this.getDOF()];
        assert(pixel_observations.size() == mosaic_coordinates.size());
        for (int i=0; i < pixel_observations.size(); i++) {

            double[] xy_px = pixel_observations.get(i);
            double[] xyz_m = mosaic_coordinates.get(i);

            double[] xyz_camera = LinAlg.transform(mosaicToCamera,
                                                   xyz_m);
            double[] xy_predicted = cameraIntrinsics.project(xyz_camera);

            residual[2*i+0] = xy_px[0] - xy_predicted[0];
            residual[2*i+1] = xy_px[1] - xy_predicted[1];
        }

        return residual;
    }

    private void computeJacobianNumerically(GIntrinsicsNode  cameraIntrinsics,
                                            GExtrinsicsNode  cameraExtrinsics,
                                            GExtrinsicsNode  mosaicExtrinsics,
                                            GNode            gn,
                                            double[][]       Jn)
    {

        double eps = 0.01;
        if (gn == cameraIntrinsics)
            eps = 0.01;
        else if (gn == cameraExtrinsics)
            eps = 0.1;
        else if (gn == mosaicExtrinsics)
            eps = 0.1;

        final double s[] = LinAlg.copy(gn.state);
        for (int i=0; i < s.length; i++) {

            gn.state[i] = s[i] + eps;
            double res_plus[] = getResidual(cameraIntrinsics, cameraExtrinsics, mosaicExtrinsics);

            gn.state[i] = s[i] - eps;
            double res_minus[] = getResidual(cameraIntrinsics, cameraExtrinsics, mosaicExtrinsics);

            for (int j=0; j < this.getDOF(); j++)
                Jn[j][i] = (res_plus[j] - res_minus[j]) / (2*eps);

            gn.state[i] = s[i];
        }
    }

    public Linearization linearize(Graph g, Linearization lin)
    {
        //long time_linearize0 = System.nanoTime();
        assert(g.nodes.get(this.nodes[CI]) instanceof GIntrinsicsNode);
        if (hasCameraExtrinsics)
            assert(g.nodes.get(this.nodes[CE]) instanceof GExtrinsicsNode);
        assert(g.nodes.get(this.nodes[ME]) instanceof GExtrinsicsNode);

        GIntrinsicsNode cameraIntrinsics = (GIntrinsicsNode) g.nodes.get(this.nodes[CI]);
        GExtrinsicsNode cameraExtrinsics = null;
        if (hasCameraExtrinsics)
            cameraExtrinsics = (GExtrinsicsNode) g.nodes.get(this.nodes[CE]);
        GExtrinsicsNode mosaicExtrinsics = (GExtrinsicsNode) g.nodes.get(this.nodes[ME]);

        if (lin == null) {
            lin = new Linearization();

            lin.J.add(new double[this.getDOF()][g.nodes.get(nodes[CI]).getDOF()]);
            if (hasCameraExtrinsics)
                lin.J.add(new double[this.getDOF()][g.nodes.get(nodes[CE]).getDOF()]);
            lin.J.add(new double[this.getDOF()][g.nodes.get(nodes[ME]).getDOF()]);

            lin.W = LinAlg.identity(this.getDOF()); // XXX use something more principled?
        }

        //long time_residual0 = System.nanoTime();
        lin.R = getResidual(cameraIntrinsics, cameraExtrinsics, mosaicExtrinsics);
        //long time_residual1 = System.nanoTime();
        //time_residual += time_residual1 - time_residual0;

        //long time_jacobian_intrinsics0 = System.nanoTime();
        computeJacobianNumerically(cameraIntrinsics, cameraExtrinsics, mosaicExtrinsics,
                                   cameraIntrinsics, lin.J.get(CI));
        //long time_jacobian_intrinsics1 = System.nanoTime();
        //time_jacobian_intrinsics += time_jacobian_intrinsics1 - time_jacobian_intrinsics0;

        if (hasCameraExtrinsics) {
            //long time_jacobian_extrinsics0 = System.nanoTime();
            computeJacobianNumerically(cameraIntrinsics, cameraExtrinsics, mosaicExtrinsics,
                                       cameraExtrinsics, lin.J.get(CE));
            //long time_jacobian_extrinsics1 = System.nanoTime();
            //time_jacobian_extrinsics += time_jacobian_extrinsics1 - time_jacobian_extrinsics0;
        }

        //long time_jacobian_mosaics0 = System.nanoTime();
        computeJacobianNumerically(cameraIntrinsics, cameraExtrinsics, mosaicExtrinsics,
                                   mosaicExtrinsics, lin.J.get(ME));
        //long time_jacobian_mosaics1 = System.nanoTime();
        //time_jacobian_mosaics += time_jacobian_mosaics1 - time_jacobian_mosaics0;

        //long time_linearize1 = System.nanoTime();
        //time_linearize += time_linearize1 - time_linearize0;

        //time_number_of_linearizations++;

        return lin;
    }

    public GEdge copy()
    {
        GTagEdge edge = new GTagEdge();
        edge.nodes = LinAlg.copy(this.nodes);
        edge.attributes = Attributes.copy(this.attributes);

        return edge;
    }

    public double getChi2(Graph g)
    {
        assert(g.nodes.get(this.nodes[CI]) instanceof GIntrinsicsNode);
        if (hasCameraExtrinsics)
            assert(g.nodes.get(this.nodes[CE]) instanceof GExtrinsicsNode);
        assert(g.nodes.get(this.nodes[ME]) instanceof GExtrinsicsNode);

        GIntrinsicsNode cameraIntrinsics = (GIntrinsicsNode) g.nodes.get(this.nodes[CI]);
        GExtrinsicsNode cameraExtrinsics = null;
        if (hasCameraExtrinsics)
            cameraExtrinsics = (GExtrinsicsNode) g.nodes.get(this.nodes[CE]);
        GExtrinsicsNode mosaicExtrinsics = (GExtrinsicsNode) g.nodes.get(this.nodes[ME]);

        double r[] = getResidual(cameraIntrinsics, cameraExtrinsics, mosaicExtrinsics);

        double chi2 = 0;
        for (int i=0; i < r.length; i++)
            chi2 += r[i]*r[i];

        return chi2;
    }

    public void write(StructureWriter outs) throws IOException
    {
        assert(false);
    }

    public void read(StructureReader ins) throws IOException
    {
        assert(false);
    }
}
