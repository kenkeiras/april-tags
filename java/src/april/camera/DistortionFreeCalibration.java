package april.camera;

import april.config.*;
import april.jmat.*;
import april.util.*;

public class DistortionFreeCalibration implements Calibration, ParameterizableCalibration
{
    // required calibration parameter lengths
    static final public int LENGTH_FC = 2;
    static final public int LENGTH_CC = 2;

    // Focal length, in pixels
    private double[]        fc;

    // Camera center
    private double[]        cc;

    // Intrinsics matrix
    private double[][]      K;
    private double[][]      Kinv;

    // Other
    private int             width;
    private int             height;

    public DistortionFreeCalibration(double fc[], double cc[],
                                    int width, int height)
    {
        this.fc     = LinAlg.copy(fc);
        this.cc     = LinAlg.copy(cc);

        this.width  = width;
        this.height = height;

        createIntrinsicsMatrix();
    }

    public DistortionFreeCalibration(Config config)
    {
        this.fc     = config.requireDoubles("intrinsics.fc");
        this.cc     = config.requireDoubles("intrinsics.cc");

        this.width  = config.requireInt("width");
        this.height = config.requireInt("height");

        createIntrinsicsMatrix();
    }

    private void createIntrinsicsMatrix()
    {
        assert(fc.length == LENGTH_FC);
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
        return CameraMath.pixelTransform(K, xy_rn);
    }

    /** Convert a 2D pixel coordinate in this view to normalized coordinates,
      * { X/Z, Y/Z }, removing distortion if appropriate.
      */
    public double[] pixelsToNorm(double xy_rp[])
    {
        return CameraMath.pixelTransform(Kinv, xy_rp);
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
        s = String.format("%s        }\n", s);

        return s;
    }

    public String getCacheString()
    {
        return String.format("%.12f %.12f %.12f %.12f %d %d",
                             fc[0], fc[1],
                             cc[0], cc[1],
                             width, height);
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Parameterizable interface methods
    public double[] getParameterization()
    {
        int len = LENGTH_FC + LENGTH_CC;

        double params[] = new double[len];

        params[0] = fc[0];
        params[1] = fc[1];

        params[2] = cc[0];
        params[3] = cc[1];

        return params;
    }

    public void resetParameterization(double params[])
    {
        assert(params.length == (LENGTH_FC + LENGTH_CC));

        fc = new double[LENGTH_FC];
        fc[0] = params[0];
        fc[1] = params[1];

        cc = new double[LENGTH_CC];
        cc[0] = params[2];
        cc[1] = params[3];

        createIntrinsicsMatrix();
    }
}
