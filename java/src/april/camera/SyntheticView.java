package april.camera;

public interface SyntheticView extends Calibration
{
    // Extends calibration, so includes getWidth, getHeight,
    // distort, rectify, and project methods. Not totally sure
    // that this is right, but it seems reasonable.

    // XXX change this?
    // Useful so far for getting distorted image boundaries
    // (width, height), which a Rasterizer might need (e.g.
    // for bounds checking). We could replace this with a few
    // specific method calls, but this might be cleaner
    public Calibration getCalibration();
}
