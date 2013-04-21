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

    double dTheta;

    double maxValidTheta; // angle off of principal (z) axis
    double maxValidPixelRadius;

    View view;
    double K[][];
    double Kinv[][];
    double cc[];

    public DistortionFunctionVerifier(View view)
    {
        this(view, Math.PI/1000); // 1000 steps over the theta range
    }

    public DistortionFunctionVerifier(View view, double dTheta)
    {
        this.view   = view;
        this.dTheta = dTheta;
        this.K      = view.copyIntrinsics();
        this.Kinv   = LinAlg.inverse(K);
        this.cc     = new double[] { K[0][2], K[1][2] };

        int width   = view.getWidth();
        int height  = view.getHeight();
        double maxObservedPixelRadius = getMaxObservedPixelRadius(cc, width, height);

        boolean functionWasNonMonotonic = false;
        double maxTheta = Math.PI; // max angle from z axis
        double lastTheta = 0;
        double lastPixelRadius = 0;

        for (double theta = 0; theta < maxTheta; theta += dTheta) {

            double x = Math.sin(theta); // if y==0, x==r. sin(theta) = O/H = r/1 = r = x
            double y = 0;
            double z = Math.cos(theta); // cos(theta) = A/H = z/1 = z

            double xyz_r[] = new double[] { x, y, z };

            double xy_dp[] = view.rayToPixels(xyz_r);
            double pixelRadius = LinAlg.distance(xy_dp, cc);

            if (pixelRadius < lastPixelRadius) {
                functionWasNonMonotonic = true;
                break;
            }

            this.maxValidTheta = theta;
            this.maxValidPixelRadius = pixelRadius;

            lastPixelRadius = pixelRadius;
            lastTheta = theta;

            // break if we're past the furthest corner in the distorted image.
            // we add a user-configurable buffer because projections just outside
            // of the image can be useful
            if (pixelRadius > (1.0 + this.radiusBuffer)*maxObservedPixelRadius)
                break;
        }

        /*
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

            double xy_dp[] = view.rayToPixels(CameraMath.rayToPlane(xy_rn));
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
            // image boundary, it's time to quit. we add an offset
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
        */
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

    /** Will this ray be valid after distortion?
      */
    public boolean validRay(double xyz_r[])
    {
        double x = xyz_r[0];
        double y = xyz_r[1];
        double z = xyz_r[2];

        double r = Math.sqrt(x*x + y*y);
        double theta = Math.atan2(r, z);

        if (theta < this.maxValidTheta)
            return true;

        return false;
    }

    /** Should this pixel coordinate undistort correctly?
      */
    public boolean validPixelCoord(double xy_dp[])
    {
        double dx = xy_dp[0] - cc[0];
        double dy = xy_dp[1] - cc[1];
        double pixelsRadius = Math.sqrt(dx*dx + dy*dy);

        if (pixelsRadius < this.maxValidPixelRadius)
            return true;

        return false;
    }

    /** If this ray is valid, return it, otherwise, return a ray at the same
      * angle about the principal axis (psi) with the maximum valid angle off
      * of the principal axis (theta) for this calibration.
      */
    public double[] clampRay(double xyz_r[])
    {
        double x = xyz_r[0];
        double y = xyz_r[1];
        double z = xyz_r[2];

        double r = Math.sqrt(x*x + y*y);
        double theta = Math.atan2(r, z);
        double psi   = Math.atan2(y, x);

        if (theta < this.maxValidTheta)
            return xyz_r;

        theta = this.maxValidTheta;

        z = Math.cos(theta); // cos(theta) = A/H = z/1 = z
        r = Math.sin(theta); // sin(theta) = O/H = r/1 = r

        x = r*Math.cos(psi);
        y = r*Math.sin(psi);

        return new double[] { x, y, z };
    }

    /** If this pixel coordinate is valid, return it, otherwise, return a
      * coordinate at the same angle about the principal axis with the maximum
      * valid pixel radius for this calibration.
      */
    public double[] clampPixels(double xy_dp[])
    {
        double relative_dp[] = LinAlg.subtract(xy_dp, cc);
        double pixelsRadius = LinAlg.magnitude(relative_dp);

        if (pixelsRadius < this.maxValidPixelRadius)
            return xy_dp;

        return LinAlg.add(cc,
                          LinAlg.scale(relative_dp,
                                       maxValidPixelRadius/pixelsRadius));
    }
}

