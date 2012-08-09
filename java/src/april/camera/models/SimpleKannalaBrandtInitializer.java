package april.camera.models;

import java.util.*;

import april.camera.*;
import april.jmat.*;
import april.tag.*;

public class SimpleKannalaBrandtInitializer implements CalibrationInitializer
{
    public static boolean verbose = true;

    public SimpleKannalaBrandtInitializer()
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

        // initialize kc to the polynomial approximation to the tangent function
        double kc[] = new double[] {  1.0/  3,
                                      2.0/ 15,
                                     17.0/315,
                                      0.0 };

        return new SimpleKannalaBrandtCalibration(fc, cc, kc, width, height);
    }

    /** Initialize the calibration using the provided parameters. Essentially,
      * create the desired class (which implements ParameterizableCalibration)
      * and reset its parameters to those provided. Don't waste time estimating
      * the parameters
      */
    public ParameterizableCalibration initializeWithParameters(int width, int height,
                                                               double params[])
    {
        return new SimpleKannalaBrandtCalibration(params, width, height);
    }
}


