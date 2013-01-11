package april.camera;

import java.util.*;

import april.jmat.*;
import april.jmat.geom.*;
import april.tag.*;
import april.util.*;

public class IntrinsicsEstimator2
{
    public static boolean verbose = true;

    private double K[][];
    private ArrayList<double[][]> vanishingPoints = new ArrayList<double[][]>();

    private TagMosaic mosaic;
    private int width;
    private int height;

    public IntrinsicsEstimator2(List<List<TagDetection>> allDetections, TagMosaic mosaic,
                                double fallbackcx, double fallbackcy)
    {
        this.mosaic = mosaic;

        // compute all of the vanishing points
        for (List<TagDetection> detections : allDetections) {

            if (detections.size() == 1)
                computeSingleTagVanishingPoints(detections.get(0));
            else if (detections.size() > 1)
                computeTagMosaicVanishingPoints(detections);
            else {
                vanishingPoints.add(null);
            }
        }

        if (verbose) {
            for (int i=0; i < vanishingPoints.size(); i++) {
                double vp[][] = vanishingPoints.get(i);
                if (vp == null)
                    System.out.printf("Vanishing point %2d: null\n", i+1);
                else
                    System.out.printf("Vanishing point %2d: (%8.1f, %8.1f) and (%8.1f, %8.1f)\n",
                                      i+1, vp[0][0], vp[0][1], vp[1][0], vp[1][1]);
            }
        }

        if (vanishingPoints.size() >= 3) {
            // if we have enough points to estimate cx and cy properly, do so
            K = CameraMath.estimateIntrinsicsFromVanishingPoints(vanishingPoints);

        } else if (vanishingPoints.size() >= 1) {
            // estimate the focal length with the fallback cx, cy given
            K = CameraMath.estimateIntrinsicsFromVanishingPointsWithGivenCxCy(vanishingPoints, fallbackcx, fallbackcy);
        }
    }

    public ArrayList<double[][]> getVanishingPoints()
    {
        return vanishingPoints;
    }

    public double[][] getIntrinsics()
    {
        if (K == null)
            return null;

        return LinAlg.copy(K);
    }

    private void computeSingleTagVanishingPoints(TagDetection d)
    {
        // lines on tag
        GLine2D bottom = new GLine2D(d.p[0], d.p[1]);
        GLine2D right  = new GLine2D(d.p[1], d.p[2]);
        GLine2D top    = new GLine2D(d.p[2], d.p[3]);
        GLine2D left   = new GLine2D(d.p[3], d.p[0]);

        // intersect lines to compute vanishing points
        vanishingPoints.add(new double[][] { bottom.intersectionWith(top) ,
                                             left.intersectionWith(right) });
    }

    private void computeTagMosaicVanishingPoints(List<TagDetection> detections)
    {
        ArrayList<double[]> xy_rcs = new ArrayList<double[]>();
        ArrayList<double[]> xy_pxs = new ArrayList<double[]>();

        for (TagDetection d : detections) {
            xy_rcs.add(new double[] { mosaic.getRow(d.id), mosaic.getColumn(d.id) });
            xy_pxs.add(d.cxy);
        }

        double H[][] = CameraMath.estimateHomography(xy_rcs, xy_pxs);

        double rowVanishingPoint[] = CameraMath.pixelTransform(H, new double[] { 0, 1, 0 });
        double colVanishingPoint[] = CameraMath.pixelTransform(H, new double[] { 1, 0, 0 });

        if (verbose)
            System.out.printf("Vanishing points (%7.1f, %7.1f) and (%7.1f, %7.1f)\n",
                              rowVanishingPoint[0], rowVanishingPoint[1],
                              colVanishingPoint[0], colVanishingPoint[1]);

        vanishingPoints.add(new double[][] { rowVanishingPoint, colVanishingPoint });
    }
}

