package april.camera;

import java.util.*;

import april.jmat.*;
import april.jmat.geom.*;
import april.tag.*;
import april.util.*;

public class IntrinsicsEstimator
{
    public static boolean verbose = true;

    private double K[][];
    private ArrayList<double[][]> vanishingPoints = new ArrayList<double[][]>();
    private ArrayList<ArrayList<double[][]>> allFitLines = new ArrayList<ArrayList<double[][]>>();

    private TagMosaic mosaic;

    /** Estimate the intrinsics by computing vanishing points from the tag
     * detections.  If only one image is provided, the fallback focal center is
     * used to allow estimation of the focal length. Lacking more information,
     * width/2 and height/2 is a good guess for the focal center.
      */
    public IntrinsicsEstimator(List<List<TagDetection>> allDetections, TagMosaic mosaic,
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
                allFitLines.add(null);
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

    public ArrayList<double[][]> getFitLines(int n)
    {
        return allFitLines.get(n);
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

        ArrayList<double[][]> fitLines = new ArrayList<double[][]>();
        fitLines.add(getLine(bottom));
        fitLines.add(getLine(top));
        fitLines.add(getLine(left));
        fitLines.add(getLine(right));
        allFitLines.add(fitLines);
    }

    private void computeTagMosaicVanishingPoints(List<TagDetection> detections)
    {
        ArrayList<TagMosaic.GroupedDetections> rowDetections = mosaic.getRowDetections(detections);
        ArrayList<TagMosaic.GroupedDetections> colDetections = mosaic.getColumnDetections(detections);

        ArrayList<double[][]> fitLines = new ArrayList<double[][]>();

        // get min and max row and column for computing vanishing point
        int minRow = Integer.MAX_VALUE;
        int maxRow = Integer.MIN_VALUE;
        int minCol = Integer.MAX_VALUE;
        int maxCol = Integer.MIN_VALUE;
        int minRowIndex = -1;
        int maxRowIndex = -1;
        int minColIndex = -1;
        int maxColIndex = -1;

        for (int i=0; i < rowDetections.size(); i++) {
            TagMosaic.GroupedDetections gd = rowDetections.get(i);
            assert(gd.type == TagMosaic.GroupedDetections.ROW_GROUP);

            if (verbose)
                System.out.printf("Detection group %2d: type %1d index %2d #detections %3d\n",
                                  i, gd.type, gd.index, gd.detections.size());

            if (gd.detections.size() < 2)
                continue;

            fitLines.add(getLine(gd.fitLine()));

            if (gd.index < minRow) {
                minRow = gd.index;
                minRowIndex = i;
            }
            if (gd.index > maxRow) {
                maxRow = gd.index;
                maxRowIndex = i;
            }
        }

        for (int i=0; i < colDetections.size(); i++) {
            TagMosaic.GroupedDetections gd = colDetections.get(i);
            assert(gd.type == TagMosaic.GroupedDetections.COL_GROUP);

            if (verbose)
                System.out.printf("Detection group %2d: type %1d index %2d #detections %3d\n",
                                  i, gd.type, gd.index, gd.detections.size());

            if (gd.detections.size() < 2)
                continue;

            fitLines.add(getLine(gd.fitLine()));

            if (gd.index < minCol) {
                minCol = gd.index;
                minColIndex = i;
            }
            if (gd.index > maxCol) {
                maxCol = gd.index;
                maxColIndex = i;
            }
        }

        if (verbose)
            System.out.printf("Row min %2d (%2d) max %2d (%2d) Col min %2d (%2d) max %2d (%2d)\n",
                              minRow, minRowIndex, maxRow, maxRowIndex,
                              minCol, minColIndex, maxCol, maxColIndex);

        // if we don't have two distinct lines, we can't do anything
        if (minRowIndex == maxRowIndex || minColIndex == maxColIndex) {
            vanishingPoints.add(null);
            allFitLines.add(fitLines);
            return;
        }

        GLine2D rowMinLine = rowDetections.get(minRowIndex).fitLine();
        GLine2D rowMaxLine = rowDetections.get(maxRowIndex).fitLine();
        GLine2D colMinLine = colDetections.get(minColIndex).fitLine();
        GLine2D colMaxLine = colDetections.get(maxColIndex).fitLine();

        double rowVanishingPoint[] = rowMinLine.intersectionWith(rowMaxLine);
        double colVanishingPoint[] = colMinLine.intersectionWith(colMaxLine);

        if (verbose)
            System.out.printf("Vanishing points (%7.1f, %7.1f) and (%7.1f, %7.1f)\n",
                              rowVanishingPoint[0], rowVanishingPoint[1],
                              colVanishingPoint[0], colVanishingPoint[1]);

        vanishingPoints.add(new double[][] { rowVanishingPoint, colVanishingPoint });
        allFitLines.add(fitLines);
    }

    // rendering
    private double[][] getLine(GLine2D line)
    {
        double x0 = -10000;
        double x1 =  20000;
        double y0 = -10000;
        double y1 =  20000;

        double p0[] = null;
        double p1[] = null;

        double dx  = line.getDx();
        double dy  = line.getDy();
        double p[] = line.getPoint();
        double t   = line.getTheta();
        t = MathUtil.mod2pi(t);

        double pi = Math.PI;
        // mostly horizontal
        if ((t < -3*pi/4) || (t > -pi/4 && t < pi/4) || (t > 3*pi/4)) {
            p0 = new double[] { x0, p[1] + (x0 - p[0])*dy/dx };
            p1 = new double[] { x1, p[1] + (x1 - p[0])*dy/dx };
        }
        // mostly vertical
        else {
            p0 = new double[] { p[0] + (y0 - p[1])*dx/dy, y0 };
            p1 = new double[] { p[0] + (y1 - p[1])*dx/dy, y1 };
        }

        return new double[][] { p0, p1 };
    }
}
