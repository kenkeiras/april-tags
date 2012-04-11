package april.camera;

import java.util.*;

import april.jmat.*;

public class StereoRectifiedView implements SyntheticView
{
    private Calibration cal;

    // Transform new rectified pixel coordinates to old rectified pixel
    // coordinates
    private double[][]  T_N2O;

    private double      Rb, Rr, Rt, Rl;

    private double[][]  K;
    private double[][]  Kinv;

    private int         width;
    private int         height;

    public StereoRectifiedView(Calibration cal, double K[][], double T_N2O[][], double XY01[][])
    {
        this.cal    = cal;
        this.T_N2O  = LinAlg.copy(T_N2O);
        this.K      = LinAlg.copy(K);
        this.Kinv   = LinAlg.inverse(K);

        Rb = XY01[0][1];
        Rl = XY01[0][0];
        Rr = XY01[1][0];
        Rt = XY01[1][1];

        width  = (int) Math.floor(Rr - Rl + 1);
        height = (int) Math.floor(Rt - Rb + 1);
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

    ////////////////////////////////////////////////////////////////////////////////
    // Calibration interface methods

    public int getWidth()
    {
        return width;
    }

    public int getHeight()
    {
        return height;
    }

    public double[][] getIntrinsics()
    {
        double res[][] = LinAlg.copy(K);

        res[0][2] -= Rl;
        res[1][2] -= Rb;

        return res;
    }

    public double[] distort(double xy_rp[])
    {
        double xy_rp_cal[] = transform3x3rescaled(T_N2O,
                                                  new double[] { xy_rp[0] + Rl ,
                                                                 xy_rp[1] + Rb });

        double xy_dp[] = cal.distort(xy_rp_cal);

        return xy_dp;
    }

    public double[] rectify(double xy_dp[])
    {
        double xy_rp_cal[] = cal.rectify(xy_dp);

        double xy_rp[] = transform3x3rescaled(LinAlg.inverse(T_N2O),
                                              xy_rp_cal);
        xy_rp[0] = xy_rp[0] - Rl;
        xy_rp[1] = xy_rp[1] - Rb;

        return xy_rp;
    }

    public double[] project(double xyz_camera[])
    {
        double xy_rn_cal[] = new double[] { xyz_camera[0] / xyz_camera[2] ,
                                        xyz_camera[1] / xyz_camera[2] };

        double xy_rp_cal[] = transform3x3rescaled(cal.getIntrinsics(), xy_rn_cal);

        double xy_rp[] = transform3x3rescaled(LinAlg.inverse(T_N2O),
                                              xy_rp_cal);
        xy_rp[0] = xy_rp[0] - Rl;
        xy_rp[1] = xy_rp[1] - Rb;

        return xy_rp;
    }

    public Calibration getCalibration()
    {
        return cal;
    }

    public String getCacheString()
    {
        String s = cal.getCacheString();

        for (int row=0; row < T_N2O.length; row++)
            for (int col=0; col < T_N2O[row].length; col++)
                s = String.format("%s, %.12f", s, T_N2O[row][col]);

        s = String.format("%s, %.12f %.12f %.12f %.12f",
                          s, Rb, Rr, Rt, Rl);

        for (int row=0; row < K.length; row++)
            for (int col=0; col < K[row].length; col++)
                s = String.format("%s, %.12f", s, K[row][col]);

        s = String.format("%s, %d, %d", s, width, height);

        return s;
    }
}
