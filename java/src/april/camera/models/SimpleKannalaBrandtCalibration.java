package april.camera.models;

import april.camera.*;
import april.config.*;
import april.jmat.*;
import april.util.*;

public class SimpleKannalaBrandtCalibration implements Calibration, ParameterizableCalibration
{
    // required calibration parameter lengths
    static final public int LENGTH_FC = 2;
    static final public int LENGTH_CC = 2;

    static final public int LENGTH_KC = 4;

    // Focal length, in pixels
    private double[]        fc;

    // Camera center
    private double[]        cc;

    // Distortion
    private double[]        kc;

    // Intrinsics matrix
    private double[][]      K;
    private double[][]      Kinv;

    // Other
    private int             width;
    private int             height;

    public SimpleKannalaBrandtCalibration(double fc[], double cc[], double kc[],
                                    int width, int height)
    {
        this.fc     = LinAlg.copy(fc);
        this.cc     = LinAlg.copy(cc);
        this.kc     = LinAlg.copy(kc);

        this.width  = width;
        this.height = height;

        createIntrinsicsMatrix();
    }

    public SimpleKannalaBrandtCalibration(Config config)
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
        assert(cc.length == LENGTH_CC);
        assert(kc.length == LENGTH_KC);

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
        s = String.format("%s            kc = [%11.6f,%11.6f,%11.6f,%11.6f ];\n",
                          s, kc[0], kc[1], kc[2], kc[3]);

        s = String.format("%s        }\n", s);

        return s;
    }

    public String getCacheString()
    {
        return String.format("%.12f %.12f %.12f %.12f %.12f %.12f %.12f %.12f %d %d",
                             fc[0], fc[1],
                             cc[0], cc[1],
                             kc[0], kc[1], kc[2], kc[3],
                             width, height);
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Parameterizable interface methods
    public double[] getParameterization()
    {
        int len = LENGTH_FC + LENGTH_CC + LENGTH_KC;

        double params[] = new double[len];

        params[ 0] = fc[0];
        params[ 1] = fc[1];

        params[ 2] = cc[0];
        params[ 3] = cc[1];

        params[ 4] = kc[0];
        params[ 5] = kc[1];
        params[ 6] = kc[2];
        params[ 7] = kc[3];

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
        kc[2] = params[6];
        kc[3] = params[7];

        createIntrinsicsMatrix();
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Private methods

    // Perform distortion in normalized coordinates
    private double[] distortNormalized(double xy_rn[])
    {
        assert(xy_rn.length == 2);

        double x = xy_rn[0];
        double y = xy_rn[1];
        double z = 1.0;

        // the three sides of the triangle
        double O = Math.sqrt(x*x + y*y);
        double H = Math.sqrt(x*x + y*y + z*z);
        double A = z;

        double theta  = Math.asin(O/H);
        double theta2 = theta*theta;
        double theta3 = theta*theta2;
        double theta5 = theta3*theta2;
        double theta7 = theta5*theta2;
        double theta9 = theta7*theta2;

        double psi = Math.atan2(y, x);

        double rtheta =         theta + // force kc0 to 1
                        kc[0] * theta3 +
                        kc[1] * theta5 +
                        kc[2] * theta7 +
                        kc[3] * theta9;

        double ur[] = new double[] { Math.cos(psi), Math.sin(psi) };

        double xy_dn[] = new double[2];

        xy_dn[0] += ur[0] * rtheta;
        xy_dn[1] += ur[1] * rtheta;

        return xy_dn;
    }

    private double[] rectifyNormalized(double xy_dn[])
    {
        double psi = Math.atan2(xy_dn[1], xy_dn[0]);

        double rtheta = ((xy_dn[0]/Math.cos(psi)) + (xy_dn[1]/Math.sin(psi))) / 2d;

        double theta = Math.asin(Math.sqrt(xy_dn[0]*xy_dn[0] + xy_dn[1]*xy_dn[1]) /
                                 Math.sqrt(xy_dn[0]*xy_dn[0] + xy_dn[1]*xy_dn[1] + 1));

        for (int i=0; i < 10; i++)
            theta = rtheta - (kc[0]*Math.pow(theta, 3) + kc[1]*Math.pow(theta, 5) + kc[2]*Math.pow(theta, 7) + kc[3]*Math.pow(theta, 9));

        double r = Math.tan(theta);

        return new double[] { r*Math.cos(psi), r*Math.sin(psi) };
    }
}


