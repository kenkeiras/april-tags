package april.camera;

import april.config.*;
import april.jmat.*;
import april.util.*;

public class CaltechCalibration implements Calibration, ParameterizableCalibration
{
    // XXX change this to run until convergence
    static final int num_iterations = 5;

    // required calibration parameter lengths
    static final public int LENGTH_FC = 2;
    static final public int LENGTH_CC = 2;
    static final public int LENGTH_KC = 5;

    // indices for lookup in kc[]
    static final public int KC1 = 0; // r^2
    static final public int KC2 = 1; // r^4
    static final public int KC3 = 2; // tangential
    static final public int KC4 = 3; // tangential
    static final public int KC5 = 4; // r^6

    // Focal length, in pixels
    private double[]        fc;

    // Camera center
    private double[]        cc;

    // Distortion
    private double[]        kc; // [kc1 kc2 kc3 kc4 kc5 kc6]

    // Skew
    private double          skew;

    // Intrinsics matrix
    private double[][]      K;
    private double[][]      Kinv;

    // Other
    private int             width;
    private int             height;

    public CaltechCalibration(double fc[], double cc[], double kc[], double skew,
                              int width, int height)
    {
        this.fc     = LinAlg.copy(fc);
        this.cc     = LinAlg.copy(cc);
        this.kc     = LinAlg.copy(kc);
        this.skew   = skew;

        this.width  = width;
        this.height = height;

        createIntrinsicsMatrix();
    }

    public CaltechCalibration(Config config)
    {
        this.fc     = config.requireDoubles("intrinsics.fc");
        this.cc     = config.requireDoubles("intrinsics.cc");
        this.kc     = config.requireDoubles("intrinsics.kc");
        this.skew   = config.requireDouble("intrinsics.skew");

        this.width  = config.requireInt("width");
        this.height = config.requireInt("height");

        createIntrinsicsMatrix();
    }

    private void createIntrinsicsMatrix()
    {
        assert(fc.length == LENGTH_FC);
        assert(kc.length == LENGTH_KC);
        assert(cc.length == LENGTH_CC);

        K = new double[][] { { fc[0],  skew*fc[0], cc[0] } ,
                             {   0.0,       fc[1], cc[1] } ,
                             {   0.0,         0.0,   1.0 } };
        Kinv = LinAlg.inverse(K);
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Calibration interface methods

    /** Return image width from calibration.
      */
    public int getWidth()
    {
        return this.width;
    }

    /** Return image height from calibration.
      */
    public int getHeight()
    {
        return this.height;
    }

    /** Return intrinsics matrix from calibration.
      */
    public double[][] getIntrinsics()
    {
        return LinAlg.copy(K);
    }

    /** Rectified pixel coordinates to distorted pixel coordinates.
      */
    public double[] distort(double xy_rp[])
    {
        double xy_rn[] = this.pixelsToNormalized(xy_rp);
        double xy_dn[] = this.distortNormalized(xy_rn);
        return this.normalizedToPixels(xy_dn);
    }

    /** Distorted pixel coordinates to rectified pixel coordinates.
      */
    public double[] rectify(double xy_dp[])
    {
        double xy_dn[] = this.pixelsToNormalized(xy_dp);
        double xy_rn[] = this.rectifyNormalized(xy_dn);
        return this.normalizedToPixels(xy_rn);
    }

    /** Project a 3D point in the appropriate coordinate frame to distorted
      * pixel coordinates.
      */
    public double[] project(double xyz_camera[])
    {
        double xy_rn[] = new double[] { xyz_camera[0] / xyz_camera[2] ,
                                        xyz_camera[1] / xyz_camera[2] };

        double xy_dn[] = this.distortNormalized(xy_rn);
        return this.normalizedToPixels(xy_dn);
    }

    public String getCacheString()
    {
        return String.format("%.12f %.12f %.12f %.12f %.12f %.12f %.12f %.12f %.12f %.12f %d %d",
                             fc[0], fc[1],
                             cc[0], cc[1],
                             kc[0], kc[1], kc[2], kc[3], kc[4],
                             skew,
                             width, height);
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Parameterizable interface methods
    public double[] getParameterization()
    {
        int len = LENGTH_FC + LENGTH_CC + LENGTH_KC + 1;

        double params[] = new double[len];

        params[0] = fc[0];
        params[1] = fc[1];

        params[2] = cc[0];
        params[3] = cc[1];

        params[4] = kc[0];
        params[5] = kc[1];
        params[6] = kc[2];
        params[7] = kc[3];
        params[8] = kc[4];

        params[9] = skew;

        return params;
    }

    public void resetParameterization(double params[])
    {
        assert(params.length == (LENGTH_FC + LENGTH_CC + LENGTH_KC + 1));

        fc = new double[LENGTH_FC];
        fc[0] = params[0];
        fc[1] = params[1];

        cc = new double[LENGTH_CC];
        cc[0] = params[2];
        cc[1] = params[3];

        kc = new double[LENGTH_KC];
        kc[0] = params[4];
        kc[1] = params[5];
        kc[2] = params[6];
        kc[3] = params[7];
        kc[4] = params[8];

        skew = params[9];

        createIntrinsicsMatrix();
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Private methods

    // Convert from normalized to pixel coordinates
    private double[] normalizedToPixels(double xy_n[])
    {
        double xy_p[][] = LinAlg.matrixAB(K,
                                          new double[][] { { xy_n[0] },
                                                           { xy_n[1] },
                                                           {       1 } });
        return new double[] { xy_p[0][0] / xy_p[2][0] ,
                              xy_p[1][0] / xy_p[2][0] };

    }

    // Convert from pixel to normalized coordinates
    private double[] pixelsToNormalized(double xy_p[])
    {
        double xy_n[][] = LinAlg.matrixAB(Kinv,
                                          new double[][] { { xy_p[0] },
                                                           { xy_p[1] },
                                                           {       1 } });
        return new double[] { xy_n[0][0] / xy_n[2][0] ,
                              xy_n[1][0] / xy_n[2][0] };
    }

    // Perform distortion in normalized coordinates
    private double[] distortNormalized(double xy_rn[])
    {
        double x = xy_rn[0];
        double y = xy_rn[1];

        double r2 = x*x + y*y;
        double r4 = r2*r2;
        double r6 = r4*r2;

        double multiplier = 1 + kc[KC1] * r2
                              + kc[KC2] * r4
                              + kc[KC5] * r6 ;

        double dx[] = new double[] {2*kc[KC3]*x*y + kc[KC4]*(r2 + 2*x*x),
                                    kc[KC3]*(r2 + 2*y*y) + 2*kc[KC4]*x*y};

        double xy_dn[] = new double[] { x*multiplier + dx[0] ,
                                        y*multiplier + dx[1] };
        return xy_dn;
    }

    // Perform iterative rectification in normalized coordinates
    private double[] rectifyNormalized(double xy_dn[])
    {
        double x = xy_dn[0];
        double y = xy_dn[1];

        for (int i=0; i < num_iterations; i++) {
            double r2 = x*x + y*y;
            double r4 = r2 * r2;
            double r6 = r4 * r2;

            double multiplier = 1 + kc[KC1] * r2
                                  + kc[KC2] * r4
                                  + kc[KC5] * r6 ;

            double dx[] = new double[] {2*kc[KC3]*x*y + kc[KC4]*(r2 + 2*x*x),
                                        kc[KC3]*(r2 + 2*y*y) + 2*kc[KC4]*x*y};

            x = (xy_dn[0] - dx[0]) / multiplier;
            y = (xy_dn[1] - dx[1]) / multiplier;
        }

        return new double[] { x, y };
    }
}
