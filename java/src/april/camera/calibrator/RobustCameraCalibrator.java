package april.camera.calibrator;

public class RobustCameraCalibrator
{
    public RobustCameraCalibrator(List<CalibrationInitializer> initializers,
                                  TagFamily tf, double metersPerTag, boolean gui)
    {

    }

    ////////////////////////////////////////////////////////////////////////////////
    // initialize parameters

    ////////////////////////////////////////////////////////////////////////////////
    // add imagery

    public void addOneImageSet(List<BufferedImage> newImages,
                               List<List<TagDetection>> newDetections,
                               double MosaicToGlobalXyzrpy[])
    {

    }

    ////////////////////////////////////////////////////////////////////////////////
    // graph optimization

    private class GraphStats
    {
        public double MRE; // mean reprojection error
        public double MSE; // mean-squared reprojection error
    }

    public List<GraphStats> iterateUntilConvergence()
    {

    }

    public List<GraphStats> iterateWithConvergenceAndReinitalization()
    {

    }

    ////////////////////////////////////////////////////////////////////////////////
    // rendering code

    public VisCanvas getVisCanvas()
    {

    }

    public void draw()
    {

    }

    ////////////////////////////////////////////////////////////////////////////////
    // file io code

    public void saveCalibrationAndImages(String basepath)
    {

    }
}
