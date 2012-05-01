package april.camera;

import april.config.*;
import april.jmat.*;
import april.util.*;

public class SimpleCaltechCalibration implements Calibration, ParameterizableCalibration
{
    // XXX change this to run until convergence
    static final int num_iterations = 5;

    // required calibration parameter lengths
    static final public int LENGTH_FC = 2;
    static final public int LENGTH_CC = 2;
    static final public int LENGTH_KC = 2;

    // indices for lookup in kc[]
    static final public int KC1 = 0; // r^2
    static final public int KC2 = 1; // r^4

    // Focal length, in pixels
    private double[]        fc;

    // Camera center
    private double[]        cc;

    // Distortion
    private double[]        kc; // [kc1 kc2]

    // Intrinsics matrix
    private double[][]      K;
    private double[][]      Kinv;

    // Other
    private int             width;
    private int             height;

    public SimpleCaltechCalibration(double fc[], double cc[], double kc[],
                              int width, int height)
    {
        this.fc     = LinAlg.copy(fc);
        this.cc     = LinAlg.copy(cc);
        this.kc     = LinAlg.copy(kc);

        this.width  = width;
        this.height = height;

        createIntrinsicsMatrix();
    }

    public SimpleCaltechCalibration(Config config)
    {
        this.fc     = config.requireDoubles("intrinsics.fc");
        this.cc     = config.requireDoubles("intrinsics.cc");
        this.kc     = config.requireDoubles("intrinsics.kc");

        this.width  = config.requireInt("width");
        this.height = config.requireInt("height");

        createIntrinsicsMatrix();
    }

    private void createIntrinsicsMatrix()
    {
        assert(fc.length == LENGTH_FC);
        assert(kc.length == LENGTH_KC);
        assert(cc.length == LENGTH_CC);

        K = new double[][] { { fc[0],   0.0, cc[0] } ,
                             {   0.0, fc[1], cc[1] } ,
                             {   0.0,   0.0,   1.0 } };
        Kinv = LinAlg.inverse(K);
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
        s = String.format("%s            kc = [%11.6f,%11.6f ];\n", s, kc[0], kc[1]);
        s = String.format("%s        }\n", s);

        return s;
    }

    public String getCacheString()
    {
        return String.format("%.12f %.12f %.12f %.12f %.12f %.12f %d %d",
                             fc[0], fc[1],
                             cc[0], cc[1],
                             kc[0], kc[1],
                             width, height);
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Parameterizable interface methods
    public double[] getParameterization()
    {
        int len = LENGTH_FC + LENGTH_CC + LENGTH_KC;

        double params[] = new double[len];

        params[0] = fc[0];
        params[1] = fc[1];

        params[2] = cc[0];
        params[3] = cc[1];

        params[4] = kc[0];
        params[5] = kc[1];

        return params;
    }

    public void resetParameterization(double params[])
    {
        assert(params.length == (LENGTH_FC + LENGTH_CC + LENGTH_KC));

        fc = new double[LENGTH_FC];
        fc[0] = params[0];
        fc[1] = params[1];

        cc = new double[LENGTH_CC];
        cc[0] = params[2];
        cc[1] = params[3];

        kc = new double[LENGTH_KC];
        kc[0] = params[4];
        kc[1] = params[5];

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

        double multiplier = 1 + kc[KC1] * r2
                              + kc[KC2] * r4;

        double xy_dn[] = new double[] { x*multiplier ,
                                        y*multiplier };
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

            double multiplier = 1 + kc[KC1] * r2
                                  + kc[KC2] * r4;

            x = (xy_dn[0]) / multiplier;
            y = (xy_dn[1]) / multiplier;
        }

        return new double[] { x, y };
    }
}
