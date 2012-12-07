package april.camera.calibrator;

import java.awt.image.*;
import java.awt.Color;
import java.util.*;

import april.camera.*;
import april.jmat.*;
import april.tag.*;
import april.vis.*;

public class RobustCameraCalibrator
{
    CameraCalibrationSystem cal;
    CalibrationRenderer renderer;

    TagFamily tf;
    TagMosaic tm;
    double metersPerTag;

    public RobustCameraCalibrator(List<CalibrationInitializer> initializers,
                                  TagFamily tf, double metersPerTag, boolean gui)
    {
        this.tf = tf;
        this.tm = new TagMosaic(tf, metersPerTag);
        this.metersPerTag = metersPerTag;

        cal = new CameraCalibrationSystem(initializers, tf, metersPerTag);

        if (gui)
            renderer = new CalibrationRenderer(cal, this.tf, this.metersPerTag);
    }

    ////////////////////////////////////////////////////////////////////////////////
    // initialize parameters

    ////////////////////////////////////////////////////////////////////////////////
    // add imagery

    public void addOneImageSet(List<BufferedImage> newImages,
                               List<List<TagDetection>> newDetections)
    {
        cal.addSingleImageSet(newImages, newDetections);

        if (renderer != null)
            renderer.updateMosaicDimensions(newDetections);
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
        return null;
    }

    public List<GraphStats> iterateWithConvergenceAndReinitalization()
    {
        return null;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // rendering code

    public VisCanvas getVisCanvas()
    {
        if (renderer == null)
            return null;

        return renderer.vc;
    }

    public void draw()
    {
        if (renderer == null)
            return;

        renderer.draw();
    }

    ////////////////////////////////////////////////////////////////////////////////
    // file io code

    public void saveCalibrationAndImages(String basepath)
    {
    }
}
