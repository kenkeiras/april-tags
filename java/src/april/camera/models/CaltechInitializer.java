package april.camera.models;

import java.util.*;

import april.camera.*;
import april.jmat.*;
import april.tag.*;

public class CaltechInitializer implements CalibrationInitializer
{
    public static boolean verbose = true;

    public CaltechInitializer()
    {
    }

    /** Initialize the calibration using the estimation process specified by
      * the initializer. Returns null if initialization could not proceed.
      */
    public ParameterizableCalibration initializeWithObservations(int width, int height,
                                        List<List<TagDetection>> allDetections,
                                        TagFamily tf)
    {
        IntrinsicsEstimator estimator = new IntrinsicsEstimator(allDetections, tf, width, height);

        double K[][] = estimator.getIntrinsics();
        if (verbose) System.out.println("Estimated intrinsics:");
        if (verbose) LinAlg.print(K);

        for (int i=0; i < K.length; i++)
            for (int j=0; j < K[i].length; j++)
                if (Double.isNaN(K[i][j]))
                    return null;

        double fc[] = new double[] {  K[0][0],  K[1][1] };
        double cc[] = new double[] {  width/2, height/2 };
        double kc[] = new double[] {  0, 0, 0, 0, 0 };
        double skew = 0;

        return new CaltechCalibration(fc, cc, kc, skew, width, height);
    }

    /** Initialize the calibration using the provided parameters. Essentially,
      * create the desired class (which implements ParameterizableCalibration)
      * and reset its parameters to those provided. Don't waste time estimating
      * the parameters
      */
    public ParameterizableCalibration initializeWithParameters(int width, int height,
                                                               double params[])
    {
        return new CaltechCalibration(params, width, height);
    }
}
