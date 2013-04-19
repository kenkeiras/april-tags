package april.camera;

import java.util.*;

import april.camera.models.*;
import april.jmat.*;
import april.util.*;

/** A DistortionFunctionVerifier attempts to determine the limit of the
 *  distortion model in the supplied View object. Once this limit is known,
 *  the DistortionFunctionVerifier can be queried to determine if it is
 *  valid to use the distortion model for a given pixel. One example of this
 *  is a lens distortion model that could be used to rectify a distorted image,
 *  but eventually degenerates (e.g. maps all pixels to a radius of zero)
 *  -- if a user were to blindly project 3D point cloud data using this distortion
 *  model, he would get points that appeared to project into the image, but
 *  should not project at all. By calling the DistortionFunctionVerifier before
 *  computing the projection via the distortion model, the user can protect himself
 *  from such degenerate behaviors.<br>
 *  <br>
 *  It should be noted that the DistortionFunctionVerifier attempts to compute
 *  when the distortion function is no longer valid, but it is not perfect.
 *  Consider supplying corner-cases found in practice to the author, Andrew
 *  Richardson (chardson@umich.edu) so the DistortionFunctionVerifier can be
 *  improved.
 */
public class DistortionFunctionVerifier
{
    private boolean warned = false;

    // How much further should we go beyond the furthest corner when looking
    // for the max radius? a value of 0.00 goes to the furthest corner exactly.
    // A value of 0.10 goes 10% beyond the corner radius
    public double radiusBuffer = 0.10;

    double maxValidNormalizedRadius;
    double maxValidPixelRadius;

    View view;
    double K[][];
    double Kinv[][];
    double cc[];

    double center_rp[];
    double center_rn[];
    double center_dp[];

    public DistortionFunctionVerifier(View view)
    {
        this.view = view;
        this.K    = view.copyIntrinsics();
        this.Kinv = LinAlg.inverse(K);
        this.cc   = new double[] { K[0][2], K[1][2] };

        int width = view.getWidth();
        int height = view.getHeight();

        double maxObservedPixelRadius = getMaxObservedPixelRadius(cc, width, height);
        int MAX_SEARCH_RADIUS = (int) (maxObservedPixelRadius*10);

        // compute the various representations for the center pixel
        center_rp = new double[] { cc[0], cc[1] };
        center_rn = CameraMath.pinholeTransform(Kinv, center_rp);
        center_dp = view.normToPixels(center_rn);
        double lastPixelRadius = 0;

        // find the max radius by starting at the focal center and increasing x
        // until either the distortion function is non-monotonic or we're outside
        // of the observed distorted image
        double xy_rp[] = new double[] { center_rp[0], center_rp[1] };

        double normBuffer[] = new double[10];
        double pixelBuffer[] = new double[10];
        int iteration = 0;
        boolean functionWasNonMonotonic = false;
        while (true)
        {
            // assuming the distortion is primarily radial, we only increase x
            xy_rp[0] += 1;

            double xy_rn[] = CameraMath.pinholeTransform(Kinv, xy_rp);
            double normalizedRadius = Math.sqrt(xy_rn[0]*xy_rn[0] + xy_rn[1]*xy_rn[1]);

            double xy_dp[] = view.normToPixels(xy_rn);
            double pixelsRadius = LinAlg.distance(xy_dp, center_dp);

            // if the distorted radius "shrinks", the distortion function
            // is no longer monotonic and is now invalid.
            if (pixelsRadius < lastPixelRadius) {
                functionWasNonMonotonic = true;
                break;
            }

            // the distortion function is good at least up until the current
            // radius, so update "rmax"
            this.maxValidNormalizedRadius = normalizedRadius;
            this.maxValidPixelRadius = pixelsRadius;

            normBuffer[iteration % normBuffer.length]   = normalizedRadius;
            pixelBuffer[iteration % pixelBuffer.length] = pixelsRadius;
            iteration++;

            lastPixelRadius = pixelsRadius;

            // if the distorted radius has now stepped outside of the distorted
            // image boundary, it's time to quit. we add a big off buffer
            // because it is useful to know if something almost projects into
            // the image (sometimes we detect tags with one corner just outside)
            if (pixelsRadius > (1.0 + this.radiusBuffer) * maxObservedPixelRadius) {
                break;
            }

            if ((xy_rp[0] - center_rp[0]) > MAX_SEARCH_RADIUS) {
                System.out.printf("Warning: Distortion function appears asymptotic. Stopping at radius %d pixels\n",
                                  MAX_SEARCH_RADIUS);
                break;
            }
        }

        if (functionWasNonMonotonic) {
            // set the max radius a few pixels in from what was found numerically
            int desiredIteration = (iteration-1 - 5 + normBuffer.length) % normBuffer.length;
            this.maxValidNormalizedRadius = normBuffer[desiredIteration];
            this.maxValidPixelRadius      = pixelBuffer[desiredIteration];
        }

        //System.out.printf("Maximum normalized radius is %12.6f\n", maxValidNormalizedRadius);
    }

    private double getMaxObservedPixelRadius(double cc[], int width, int height)
    {
        double max = 0;

        max = Math.max(max, LinAlg.distance(cc, new double[] {      0,        0}));
        max = Math.max(max, LinAlg.distance(cc, new double[] {width-1,        0}));
        max = Math.max(max, LinAlg.distance(cc, new double[] {      0, height-1}));
        max = Math.max(max, LinAlg.distance(cc, new double[] {width-1, height-1}));

        return max;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // public methods

    /** Will this rectified, normalized coordinate be valid after distortion?
      */
    public boolean validNormalizedCoord(double xy_rn[])
    {
        double normalizedRadius = Math.sqrt(xy_rn[0]*xy_rn[0] + xy_rn[1]*xy_rn[1]);

        if (normalizedRadius < this.maxValidNormalizedRadius)
            return true;

        return false;
    }

    /** Should this pixel coordinate undistort correctly?
      */
    public boolean validPixelCoord(double xy_dp[])
    {
        double dx = xy_dp[0] - center_dp[0];
        double dy = xy_dp[1] - center_dp[1];
        double pixelsRadius = Math.sqrt(dx*dx + dy*dy);

        if (pixelsRadius < this.maxValidPixelRadius)
            return true;

        return false;
    }

    /** If this normalized coordinate is valid, return it, otherwise, return a
      * coordinate in the same direction from the focal center with the maximum
      * valid normalizd radius for this calibration.
      */
    public double[] clampNormalized(double xy_rn[])
    {
        double normalizedRadius = Math.sqrt(xy_rn[0]*xy_rn[0] + xy_rn[1]*xy_rn[1]);

        if (normalizedRadius < this.maxValidNormalizedRadius)
            return xy_rn;

        return LinAlg.scale(LinAlg.normalize(xy_rn),
                            maxValidNormalizedRadius);
    }

    /** If this pixel coordinate is valid, return it, otherwise, return a
      * coordinate in the same direction from the focal center with the maximum
      * valid pixel radius for this calibration.
      */
    public double[] clampPixels(double xy_dp[])
    {
        double relative_dp[] = LinAlg.subtract(xy_dp, center_dp);
        double pixelsRadius = LinAlg.magnitude(relative_dp);

        if (pixelsRadius < this.maxValidPixelRadius)
            return xy_dp;

        return LinAlg.add(center_dp,
                          LinAlg.scale(relative_dp,
                                       maxValidPixelRadius/pixelsRadius));
    }
}
