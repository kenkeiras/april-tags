package april.camera.calibrator;

import java.util.*;
import april.tag.*;
import april.graph.*;
import april.jmat.*;
import java.awt.image.*;

// You must instantiate a new FrameScorer each time the currentCal changes
public class PixErrScorer implements FrameScorer
{

    final CameraCalibrator currentCal;

    public PixErrScorer(CameraCalibrator _currentCal)
    {
        currentCal = _currentCal;

        // Compute any initial state
    }


    public double scoreFrame(List<TagDetection> dets)
    {

        CameraCalibrator cal = currentCal.copy();


        BufferedImage im = null;
        cal.addImages(Arrays.asList(im), Arrays.asList(dets));


        try {
            int itrs = cal.iterateUntilConvergence(.01, 2, 1000);
        } catch(RuntimeException e) {
            return Double.NaN;
        }


        double mu[] = null;
        double P[][] = null;

        try {
            Graph g = cal.getGraphCopy();
            mu = LinAlg.copy(g.nodes.get(0).state);

            P = GraphUtil.getMarginalCovariance(g, 0).copyArray();
            //P = CalUtil.getCondCov(g).copyArray();
        } catch(RuntimeException e){
            return Double.NaN;
        }

        //XXX Continue porting

        return Double.NaN;
    }

}