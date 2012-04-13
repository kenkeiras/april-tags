package april.camera.cal;

import java.awt.*;
import java.util.*;
import javax.swing.*;

import april.jmat.*;
import april.vis.*;

public class StereoRectification
{
    Calibration cal_A;
    Calibration cal_B;

    double  C2G_A[][];
    double  C2G_B[][];

    StereoRectifiedView viewA;
    StereoRectifiedView viewB;

    double  K[][];
    double  C2G_A_new[][];
    double  C2G_B_new[][];

    double  T_N2O_A[][];
    double  T_N2O_B[][];

    double  R_N2O_A[][];

    private StereoRectification(Calibration cal_A, Calibration cal_B,
                                double[][] camToGlobal_A, double[][] camToGlobal_B)
    {
        this.cal_A = cal_A;
        this.cal_B = cal_B;

        this.C2G_A = camToGlobal_A;
        this.C2G_B = camToGlobal_B;
    }

    public static StereoRectification getMaxInscribedSR(Calibration cal_A, Calibration cal_B,
                                                        double[][] camToGlobal_A, double[][] camToGlobal_B)
    {
        StereoRectification sr = new StereoRectification(cal_A, cal_B, camToGlobal_A, camToGlobal_B);
        sr.computeTransformations();
        sr.createMaxInscribedSRViews();

        return sr;
    }

    public static StereoRectification getMaxSR(Calibration cal_A, Calibration cal_B,
                                               double[][] camToGlobal_A, double[][] camToGlobal_B)
    {
        StereoRectification sr = new StereoRectification(cal_A, cal_B, camToGlobal_A, camToGlobal_B);
        sr.computeTransformations();
        sr.createMaxSRViews();

        return sr;
    }

    void computeTransformations()
    {
        ////////////////////////////////////////////////////////////////////////////////
        // New intrinsics
        double K_A[][] = LinAlg.copy(cal_A.getIntrinsics());
        double K_B[][] = LinAlg.copy(cal_B.getIntrinsics());

        K  = LinAlg.scale(LinAlg.add(K_A, K_B), 0.5);
        K[0][1] = 0; // no skew

        ////////////////////////////////////////////////////////////////////////////////
        // Transform pixels
        double c1[] = LinAlg.transform(C2G_A, new double[] { 0, 0, 0 });
        double c2[] = LinAlg.transform(C2G_B, new double[] { 0, 0, 0 });

        double vx[] = LinAlg.normalize(LinAlg.subtract(c2, c1));
        // vy might not be right. it isn't well tested, as the left camera is usually at the origin
        double vy[] = LinAlg.normalize(LinAlg.crossProduct(new double[] {C2G_A[0][2], C2G_A[1][2], C2G_A[2][2]},
                                                           vx));
        double vz[] = LinAlg.normalize(LinAlg.crossProduct(vx, vy));

        R_N2O_A = new double[][] { { vx[0], vy[0], vz[0] } ,
                                   { vx[1], vy[1], vz[1] } ,
                                   { vx[2], vy[2], vz[2] } };

        T_N2O_A = LinAlg.multiplyMany(K_A,
                                      R_N2O_A,
                                      LinAlg.inverse(K));
        T_N2O_B = LinAlg.multiplyMany(K_B,
                                      LinAlg.inverse(LinAlg.select(C2G_B, 0, 2, 0, 2)),
                                      LinAlg.select(C2G_A, 0, 2, 0, 2),
                                      R_N2O_A,
                                      LinAlg.inverse(K));

        ////////////////////////////////////////////////////////////////////////////////
        // New extrinsics
        double Rm_N2O_A[][]  = new double[][] { { R_N2O_A[0][0], R_N2O_A[0][1], R_N2O_A[0][2], 0},
                                                { R_N2O_A[1][0], R_N2O_A[1][1], R_N2O_A[1][2], 0},
                                                { R_N2O_A[2][0], R_N2O_A[2][1], R_N2O_A[2][2], 0},
                                                {             0,             0,             0, 1 } };

        double C2G_A_rot[][] = new double[][] { { C2G_A[0][0], C2G_A[0][1], C2G_A[0][2], 0 },
                                                { C2G_A[1][0], C2G_A[1][1], C2G_A[1][2], 0 },
                                                { C2G_A[2][0], C2G_A[2][1], C2G_A[2][2], 0 },
                                                {           0,           0,           0, 1 } };

        double C2G_B_rot[][] = new double[][] { { C2G_B[0][0], C2G_B[0][1], C2G_B[0][2], 0 },
                                                { C2G_B[1][0], C2G_B[1][1], C2G_B[1][2], 0 },
                                                { C2G_B[2][0], C2G_B[2][1], C2G_B[2][2], 0 },
                                                {           0,           0,           0, 1 } };

        C2G_A_new = LinAlg.multiplyMany(C2G_A,
                                        Rm_N2O_A);
        C2G_B_new = LinAlg.multiplyMany(C2G_B,
                                        LinAlg.inverse(C2G_B_rot),
                                        C2G_A_rot,
                                        Rm_N2O_A);
    }

    public void showDebuggingGUI()
    {
        JFrame jf = new JFrame("Debug");
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setLayout(new BorderLayout());

        VisWorld vw = new VisWorld();
        VisLayer vl = new VisLayer(vw);
        VisCanvas vc = new VisCanvas(vl);

        jf.add(vc, BorderLayout.CENTER);
        jf.setSize(800, 600);
        jf.setVisible(true);

        VisWorld.Buffer vb;

        vb = vw.getBuffer("initial axes");

        vb.addBack(new VisChain(C2G_A,
                                    LinAlg.scale(0.05, 0.05, 0.05),
                                    new VzAxes()));
        vb.addBack(new VisChain(C2G_B,
                                    LinAlg.scale(0.05, 0.05, 0.05),
                                    new VzAxes()));
        vb.swap();

        vb = vw.getBuffer("new axes");
        vb.addBack(new VisChain(C2G_A_new,
                                    LinAlg.scale(0.05, 0.05, 0.05),
                                    new VzAxes()));

        vb.addBack(new VisChain(C2G_B_new,
                                    LinAlg.scale(0.05, 0.05, 0.05),
                                    new VzAxes()));

        double c1[] = LinAlg.transform(C2G_A_new, new double[] { 0, 0, 0 });
        double c2[] = LinAlg.transform(C2G_B_new, new double[] { 0, 0, 0 });
        vb.addBack(new VzLines(new VisVertexData(new double[][] {c1, c2}),
                               VzLines.LINE_STRIP,
                               new VzLines.Style(Color.white, 1)));
        vb.swap();
    }

    public ArrayList<SyntheticView> getViews()
    {
        assert(viewA != null);
        assert(viewB != null);

        ArrayList<SyntheticView> views = new ArrayList<SyntheticView>();
        views.add(viewA);
        views.add(viewB);
        return views;
    }

    public ArrayList<double[][]> getExtrinsics()
    {
        ArrayList<double[][]> extrinsics = new ArrayList<double[][]>();
        extrinsics.add(C2G_A_new);
        extrinsics.add(C2G_B_new);
        return extrinsics;
    }

    private void createMaxInscribedSRViews()
    {
        double XY01_A[][] = computeMaxInscribedRectifiedRectangle(cal_A, T_N2O_A);
        double XY01_B[][] = computeMaxInscribedRectifiedRectangle(cal_B, T_N2O_B);

        if ((XY01_A[0][1] >= XY01_A[1][1]) || (XY01_A[0][0] >= XY01_A[1][0]) ||
            (XY01_B[0][1] >= XY01_B[1][1]) || (XY01_B[0][0] >= XY01_B[1][0]))
        {
            System.err.println("Error: image rotation appears too severe for a 'max inscribed rectangle'. Try a 'max rectangle'");
            assert(false);
        }

        // make sure that the Y offsets are shared. this ensures that the y
        // indices into the image match
        XY01_A[0][1] = Math.max(XY01_A[0][1], XY01_B[0][1]);
        XY01_A[1][1] = Math.min(XY01_A[1][1], XY01_B[1][1]);
        XY01_B[0][1] = XY01_A[0][1];
        XY01_B[1][1] = XY01_A[1][1];

        viewA = new StereoRectifiedView(cal_A, K, T_N2O_A, XY01_A);
        viewB = new StereoRectifiedView(cal_B, K, T_N2O_B, XY01_B);
        assert(viewA.getHeight() == viewB.getHeight());
    }

    private void createMaxSRViews()
    {
        double XY01_A[][] = computeMaxRectifiedRectangle(cal_A, T_N2O_A);
        double XY01_B[][] = computeMaxRectifiedRectangle(cal_B, T_N2O_B);

        // make sure that the Y offsets are shared. this ensures that the y
        // indices into the image match
        XY01_A[0][1] = Math.min(XY01_A[0][1], XY01_B[0][1]);
        XY01_A[1][1] = Math.max(XY01_A[1][1], XY01_B[1][1]);
        XY01_B[0][1] = XY01_A[0][1];
        XY01_B[1][1] = XY01_A[1][1];

        viewA = new StereoRectifiedView(cal_A, K, T_N2O_A, XY01_A);
        viewB = new StereoRectifiedView(cal_B, K, T_N2O_B, XY01_B);
        assert(viewA.getHeight() == viewB.getHeight());
    }

    private static double[][] computeMaxInscribedRectifiedRectangle(Calibration cal, double T_N2O[][])
    {
        double T_O2N[][] = LinAlg.inverse(T_N2O);

        double Rb, Rt, Rl, Rr;
        int x_dp, y_dp;

        // initialize bounds
        {
            double xy_rp[];

            xy_rp = cal.rectify(new double[] {                0,                 0});
            Rb = transform3x3rescaled(T_O2N, xy_rp)[1];

            xy_rp = cal.rectify(new double[] { cal.getWidth()-1,                 0});
            Rr = transform3x3rescaled(T_O2N, xy_rp)[0];

            xy_rp = cal.rectify(new double[] { cal.getWidth()-1, cal.getHeight()-1});
            Rt = transform3x3rescaled(T_O2N, xy_rp)[1];

            xy_rp = cal.rectify(new double[] {                0, cal.getHeight()-1});
            Rl = transform3x3rescaled(T_O2N, xy_rp)[0];
        }

        // TL -> TR
        y_dp = 0;
        for (x_dp = 0; x_dp < cal.getWidth(); x_dp++) {

            double xy_rp[] = cal.rectify(new double[] { x_dp, y_dp });
            xy_rp = transform3x3rescaled(T_O2N, xy_rp);
            Rb = Math.max(Rb, xy_rp[1]);
        }

        // TR -> BR
        x_dp = cal.getWidth()-1;
        for (y_dp = 0; y_dp < cal.getHeight(); y_dp++) {

            double xy_rp[] = cal.rectify(new double[] { x_dp, y_dp });
            xy_rp = transform3x3rescaled(T_O2N, xy_rp);
            Rr = Math.min(Rr, xy_rp[0]);
        }

        // BR -> BL
        y_dp = cal.getHeight()-1;
        for (x_dp = cal.getWidth()-1; x_dp >= 0; x_dp--) {

            double xy_rp[] = cal.rectify(new double[] { x_dp, y_dp });
            xy_rp = transform3x3rescaled(T_O2N, xy_rp);
            Rt = Math.min(Rt, xy_rp[1]);
        }

        // BL -> TL
        x_dp = 0;
        for (y_dp = cal.getHeight()-1; y_dp >= 0; y_dp--) {

            double xy_rp[] = cal.rectify(new double[] { x_dp, y_dp });
            xy_rp = transform3x3rescaled(T_O2N, xy_rp);
            Rl = Math.max(Rl, xy_rp[0]);
        }

        System.out.printf("Bottom: %5.1f Right: %5.1f Top: %5.1f Left: %5.1f\n", Rb, Rr, Rt, Rl);

        return new double[][] { { Rl, Rb }, { Rr, Rt } };
    }

    private static double[][] computeMaxRectifiedRectangle(Calibration cal, double T_N2O[][])
    {
        double T_O2N[][] = LinAlg.inverse(T_N2O);

        double Rb, Rt, Rl, Rr;
        int x_dp, y_dp;

        // initialize bounds
        {
            double xy_rp[];

            xy_rp = cal.rectify(new double[] {                0,                 0});
            Rb = transform3x3rescaled(T_O2N, xy_rp)[1];

            xy_rp = cal.rectify(new double[] { cal.getWidth()-1,                 0});
            Rr = transform3x3rescaled(T_O2N, xy_rp)[0];

            xy_rp = cal.rectify(new double[] { cal.getWidth()-1, cal.getHeight()-1});
            Rt = transform3x3rescaled(T_O2N, xy_rp)[1];

            xy_rp = cal.rectify(new double[] {                0, cal.getHeight()-1});
            Rl = transform3x3rescaled(T_O2N, xy_rp)[0];
        }

        // TL -> TR
        y_dp = 0;
        for (x_dp = 0; x_dp < cal.getWidth(); x_dp++) {

            double xy_rp[] = cal.rectify(new double[] { x_dp, y_dp });
            xy_rp = transform3x3rescaled(T_O2N, xy_rp);
            Rb = Math.min(Rb, xy_rp[1]);
        }

        // TR -> BR
        x_dp = cal.getWidth()-1;
        for (y_dp = 0; y_dp < cal.getHeight(); y_dp++) {

            double xy_rp[] = cal.rectify(new double[] { x_dp, y_dp });
            xy_rp = transform3x3rescaled(T_O2N, xy_rp);
            Rr = Math.max(Rr, xy_rp[0]);
        }

        // BR -> BL
        y_dp = cal.getHeight()-1;
        for (x_dp = cal.getWidth()-1; x_dp >= 0; x_dp--) {

            double xy_rp[] = cal.rectify(new double[] { x_dp, y_dp });
            xy_rp = transform3x3rescaled(T_O2N, xy_rp);
            Rt = Math.max(Rt, xy_rp[1]);
        }

        // BL -> TL
        x_dp = 0;
        for (y_dp = cal.getHeight()-1; y_dp >= 0; y_dp--) {

            double xy_rp[] = cal.rectify(new double[] { x_dp, y_dp });
            xy_rp = transform3x3rescaled(T_O2N, xy_rp);
            Rl = Math.min(Rl, xy_rp[0]);
        }

        System.out.printf("Bottom: %5.1f Right: %5.1f Top: %5.1f Left: %5.1f\n", Rb, Rr, Rt, Rl);

        return new double[][] { { Rl, Rb }, { Rr, Rt } };
    }

    private static double[] transform3x3rescaled(double T[][], double p[])
    {
        assert(T.length == 3 && p.length == 2);

        double r[] = new double[] { T[0][0]*p[0] + T[0][1]*p[1] + T[0][2],
                                    T[1][0]*p[0] + T[1][1]*p[1] + T[1][2],
                                    T[2][0]*p[0] + T[2][1]*p[1] + T[2][2] };
        r[0] = r[0] / r[2];
        r[1] = r[1] / r[2];
        r[2] = r[2] / r[2];
        return r;
    }
}

