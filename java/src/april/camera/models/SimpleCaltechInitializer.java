package april.camera.models;

import java.util.*;

import april.camera.*;
import april.jmat.*;
import april.tag.*;

public class SimpleCaltechInitializer implements CalibrationInitializer
{
    public static boolean verbose = true;

    public SimpleCaltechInitializer()
    {
    }

    /** Initialize the calibration using the estimation process specified by
      * the initializer. Returns null if initialization could not proceed.
      */
    public ParameterizableCalibration initializeWithObservations(int width, int height,
                                        List<List<TagDetection>> allDetections,
                                        TagFamily tf)
    {
        IntrinsicsFreeDistortionEstimator distortionEstimator =
                        new IntrinsicsFreeDistortionEstimator(allDetections, tf, width, height);

        ArrayList<ArrayList<TagDetection>> allRectifiedDetections = new ArrayList<ArrayList<TagDetection>>();
        for (ArrayList<TagDetection> detections : allDetections) {

            ArrayList<TagDetection> rectifiedDetections = new ArrayList<TagDetection>();
            for (TagDetection d : detections) {

                TagDetection rd = new TagDetection();
                // easy stuff
                rd.good                 = d.good;
                rd.obsCode              = d.obsCode;
                rd.code                 = d.code;
                rd.id                   = d.id;
                rd.hammingDistance      = d.hammingDistance;
                rd.rotation             = d.rotation;

                // these things could be fixed, but aren't used by IntrinsicsEstimator
                rd.p                    = LinAlg.copy(d.p);
                rd.homography           = LinAlg.copy(d.homography);
                rd.hxy                  = LinAlg.copy(d.hxy);
                rd.observedPerimeter    = d.observedPerimeter;

                // we need to fix this for IntrinsicsEstimator
                rd.cxy                  = distortionEstimator.undistort(d.cxy);

                rectifiedDetections.add(rd);
            }

            allRectifiedDetections.add(rectifiedDetections);
        }

        IntrinsicsEstimator intrinsicsEstimator = new IntrinsicsEstimator(allRectifiedDetections, tf, width/2, height/2);

        double K[][] = intrinsicsEstimator.getIntrinsics();
        if (K == null)
            return null;

        if (verbose) System.out.println("Estimated intrinsics:");
        if (verbose) LinAlg.print(K);

        for (int i=0; i < K.length; i++)
            for (int j=0; j < K[i].length; j++)
                if (Double.isNaN(K[i][j]))
                    return null;

        double fc[] = new double[] {  K[0][0],  K[1][1] };
        double cc[] = new double[] {  width/2, height/2 };
        double kc[] = new double[] {      0.0,      0.0 };

        return new SimpleCaltechCalibration(fc, cc, kc, width, height);
    }

    /** Initialize the calibration using the provided parameters. Essentially,
      * create the desired class (which implements ParameterizableCalibration)
      * and reset its parameters to those provided. Don't waste time estimating
      * the parameters
      */
    public ParameterizableCalibration initializeWithParameters(int width, int height,
                                                               double params[])
    {
        return new SimpleCaltechCalibration(params, width, height);
    }
}
