package april.camera;

public interface View
{
    /** Return max width. Valid pixel values for this
      * view range from 0 to width.
      */
    public int          getWidth();

    /** Return max height. Valid pixel values for this
      * view range from 0 to height.
      */
    public int          getHeight();

    /** Return intrinsics matrix.
      */
    public double[][]   copyIntrinsics();

    /** Convert a 2D double { X/Z, Y/Z } to pixel coordinates in this view,
      * applying distortion if appropriate.
      */
    public double[]     normToPixels(double xy_n[]);

    /** Convert a 2D pixel coordinate in this view to normalized coordinates,
      * { X/Z, Y/Z }, removing distortion if appropriate.
      */
    public double[]     pixelsToNorm(double xy_p[]);

    /** Return a string of all critical parameters for caching data based
      * on a calibration (e.g. lookup tables).
      */
    public String       getCacheString();
}

