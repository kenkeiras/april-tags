package april.camera.calibrator;

import java.util.*;
import april.tag.*;
import april.graph.*;
import april.jmat.*;
import java.awt.image.*;

import april.camera.*;

// You must instantiate a new FrameScorer each time the currentCal changes
public class PixErrScorer implements FrameScorer
{
    final static boolean useMarginal = true; // can either use marginal (slow) or conditional (faster, not "correct")

    // Do not change seed during a run!
    public static int seed = 14;
    public static int nsamples = 50;

    final CameraCalibrator currentCal;

    final int width, height;
    BufferedImage fakeIm;

    public PixErrScorer(CameraCalibrator _currentCal, int _width, int _height)
    {
        currentCal = _currentCal;

        width = _width;
        height = _height;
        fakeIm = new BufferedImage(width,height, BufferedImage.TYPE_BYTE_BINARY); // cheapest
    }


    public double scoreFrame(List<TagDetection> dets)
    {
        CameraCalibrator cal = currentCal.copy();

        // XXX Passing null here
        cal.addImages(Arrays.asList(fakeIm), Arrays.asList(dets));
        try {
            int itrs = cal.iterateUntilConvergence(.01, 2, 1000);
        } catch(RuntimeException e) {
            return Double.NaN;
        }

        // Compute mean, covariance from which we will sample
        MultiGaussian mg = null;
        try {
            Graph g = cal.getGraphCopy();
            double mu[] = LinAlg.copy(g.nodes.get(0).state);
            double P[][] = (useMarginal?
                            GraphUtil.getMarginalCovariance(g, 0).copyArray() :
                            GraphUtil.getConditionalCovariance(g, 0).copyArray());

            mg = new MultiGaussian(P, mu);
        } catch(RuntimeException e){
            return Double.NaN;
        }

        ArrayList<double[]> samples = mg.sampleMany(new Random(seed), nsamples);



        double errMeanVar[] = computeMaxErrorDist(mg.getMean(), samples, 5,
                                                  cal.getInitializers().get(0), width, height);

        return errMeanVar[0];
    }


    // Returns the distribution corresponding to the worst point on the grid
    public static double[] computeMaxErrorDist(double mean[], List<double[]> samples, int gridSz,
                                               CalibrationInitializer init, int width, int height)
    {
        // Where do we want to sample? We need a way to determine which 3D points will project?

        ArrayList<ParameterizableCalibration> cals = new ArrayList();
        for (double p[] : samples)
            cals.add(init.initializeWithParameters(width, height, p));

        ParameterizableCalibration meanCal = init.initializeWithParameters(width, height, mean);

        ArrayList<MultiGaussian> errSamples = new ArrayList();
        for (int i = 0; i < gridSz; i++)
            for (int j = 0; j < gridSz; j++) {
                int x = (width-1) * j / (gridSz - 1);
                int y = (height-1) * i / (gridSz - 1);

                int idx = y*width + x;
                double meanPix [] = {x,y};
                double meanNorm[] = meanCal.pixelsToNorm(meanPix);

                MultiGaussianEstimator mge = new MultiGaussianEstimator(1);
                for (View cal : cals) {
                    double samplePix[] = cal.normToPixels(meanNorm);
                    mge.observe(new double[]{LinAlg.distance(samplePix, meanPix)});
                }
                errSamples.add(mge.getEstimate());
            }

        Collections.sort(errSamples, new Comparator<MultiGaussian>(){
                public int compare(MultiGaussian m1, MultiGaussian m2)
                {
                    assert(m1.getDimension() == m2.getDimension());
                    assert(m1.getDimension() == 1);
                    return Double.compare(m1.getMean()[0],
                                          m2.getMean()[0]);
                }
            });


        // These are actually single-variate gaussians...
        MultiGaussian mgWorst = errSamples.get(errSamples.size()-1);

        return new double[]{mgWorst.getMean()[0], mgWorst.getCovariance().get(0,0)};
    }

}