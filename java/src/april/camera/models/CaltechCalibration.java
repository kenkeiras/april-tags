package april.camera.models;

import april.camera.*;
import april.config.*;
import april.jmat.*;
import april.util.*;

public class CaltechCalibration implements Calibration, ParameterizableCalibration
{
    // constants for iteratively rectifying coordinates (e.g. max allowed error)
    private static final int max_iterations = 20;
    private static final double max_pixel_error = 0.01;
    private double max_sqerr;

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

    public CaltechCalibration(double params[], int width, int height)
    {
        this.width = width;
        this.height = height;

        resetParameterization(params);
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

        // compute the max square error for iterative rectification in normalized units
        max_sqerr = Math.pow(max_pixel_error / Math.max(fc[0], fc[1]), 2);
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Calibration interface methods

    /** Return image width from calibration.
      */
    public int getWidth()
    {
        return width;
    }

    /** Return image height from calibration.
      */
    public int getHeight()
    {
        return height;
    }

    /** Return intrinsics matrix from calibration.
      */
    public double[][] copyIntrinsics()
    {
        return LinAlg.copy(K);
    }

    /** Convert a 2D double { X/Z, Y/Z } to pixel coordinates in this view,
      * applying distortion if appropriate.
      */
    public double[] normToPixels(double xy_rn[])
    {
        double xy_dn[] = distortNormalized(xy_rn);
        return CameraMath.pixelTransform(K, xy_dn);
    }

    /** Convert a 2D pixel coordinate in this view to normalized coordinates,
      * { X/Z, Y/Z }, removing distortion if appropriate.
      */
    public double[] pixelsToNorm(double xy_dp[])
    {
        double xy_dn[] = CameraMath.pixelTransform(Kinv, xy_dp);
        return rectifyNormalized(xy_dn);
    }

    /** Return a string of all critical parameters for caching data based
      * on a calibration (e.g. lookup tables).
      */
    public String getCalibrationString()
    {
        String s;

        s = String.format(  "        class = \"%s\";\n\n", this.getClass().getName());
        s = String.format("%s        width = %d;\n", s, width);
        s = String.format("%s        height = %d;\n\n", s, height);
        s = String.format("%s        intrinsics {\n", s);
        s = String.format("%s            fc = [%11.6f,%11.6f ];\n", s, fc[0], fc[1]);
        s = String.format("%s            cc = [%11.6f,%11.6f ];\n", s, cc[0], cc[1]);
        s = String.format("%s            kc = [%11.6f,%11.6f,%11.6f,%11.6f,%11.6f ];\n",
                          s, kc[0], kc[1], kc[2], kc[3], kc[4]);
        s = String.format("%s            skew = %11.6f\n", s, skew);
        s = String.format("%s        }\n", s);

        return s;
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
        double x_rn = xy_dn[0];
        double y_rn = xy_dn[1];

        for (int i=0; i < max_iterations; i++) {
            double r2 = x_rn*x_rn + y_rn*y_rn;
            double r4 = r2 * r2;
            double r6 = r4 * r2;

            double multiplier = 1 + kc[KC1] * r2
                                  + kc[KC2] * r4
                                  + kc[KC5] * r6 ;

            double dx[] = new double[] {2*kc[KC3]*x_rn*y_rn + kc[KC4]*(r2 + 2*x_rn*x_rn),
                                        kc[KC3]*(r2 + 2*y_rn*y_rn) + 2*kc[KC4]*x_rn*y_rn};

            double x_sqerr = xy_dn[0] - (x_rn*multiplier + dx[0]);
            double y_sqerr = xy_dn[1] - (y_rn*multiplier + dx[1]);
            double sqerr = x_sqerr*x_sqerr + y_sqerr*y_sqerr;

            x_rn = (xy_dn[0] - dx[0]) / multiplier;
            y_rn = (xy_dn[1] - dx[1]) / multiplier;

            if (sqerr < this.max_sqerr)
                break;
        }

        return new double[] { x_rn, y_rn };
    }
}
