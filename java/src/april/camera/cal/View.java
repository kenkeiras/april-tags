package april.camera.cal;

public interface View
{
    /** Return max width. Usually from calibration.
      */
    public int          getWidth();

    /** Return max height. Usually from calibration.
      */
    public int          getHeight();

    /** Return intrinsics matrix.
      */
    public double[][]   getIntrinsics();

    /** Rectified pixel coordinates to distorted pixel coordinates.
      */
    public double[]     distort(double xy_rp[]);

    /** Distorted pixel coordinates to rectified pixel coordinates.
      */
    public double[]     rectify(double xy_dp[]);

    /** Project a 3D point in the appropriate coordinate frame to distorted
      * pixel coordinates.
      */
    public double[]     project(double xyz_camera[]);

    /** Return a string of all critical parameters for caching data based
      * on a calibration (e.g. lookup tables).
      */
    public String       getCacheString();
}

