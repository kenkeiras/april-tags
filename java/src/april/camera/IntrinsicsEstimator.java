package april.camera;

import java.util.*;

import april.jmat.*;
import april.jmat.geom.*;
import april.tag.*;
import april.util.*;

public class IntrinsicsEstimator
{
    private double K[][];
    private ArrayList<double[][]> vanishingPoints = new ArrayList<double[][]>();
    private ArrayList<ArrayList<double[][]>> allFitLines = new ArrayList<ArrayList<double[][]>>();

    private TagMosaic mosaic;

    public IntrinsicsEstimator(ArrayList<ArrayList<TagDetection>> allDetections, TagFamily tf)
    {
        mosaic = new TagMosaic(tf);

        // compute all of the vanishing points
        for (ArrayList<TagDetection> detections : allDetections) {

            if (detections.size() == 1)
                computeSingleTagVanishingPoints(detections.get(0));
            else if (detections.size() > 1)
                computeTagMosaicVanishingPoints(detections);
            else {
                allFitLines.add(null);
                vanishingPoints.add(null);
            }
        }

        //for (int i=0; i < vanishingPoints.size(); i++) {
        //    double vp[][] = vanishingPoints.get(i);
        //    if (vp == null)
        //        System.out.printf("Vanishing point %2d: null\n", i+1);
        //    else
        //        System.out.printf("Vanishing point %2d: (%8.1f, %8.1f) and (%8.1f, %8.1f)\n",
        //                          i+1, vp[0][0], vp[0][1], vp[1][0], vp[1][1]);
        //}

        K = CameraMath.estimateIntrinsicsFromVanishingPoints(vanishingPoints);
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

    private void computeTagMosaicVanishingPoints(ArrayList<TagDetection> detections)
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

            //System.out.printf("Detection group %2d: type %1d index %2d #detections %3d\n",
            //                  i, gd.type, gd.index, gd.detections.size());

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

            //System.out.printf("Detection group %2d: type %1d index %2d #detections %3d\n",
            //                  i, gd.type, gd.index, gd.detections.size());

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

        //System.out.printf("Row min %2d (%2d) max %2d (%2d) Col min %2d (%2d) max %2d (%2d)\n",
        //                  minRow, minRowIndex, maxRow, maxRowIndex,
        //                  minCol, minColIndex, maxCol, maxColIndex);

        GLine2D rowMinLine = rowDetections.get(minRowIndex).fitLine();
        GLine2D rowMaxLine = rowDetections.get(maxRowIndex).fitLine();
        GLine2D colMinLine = colDetections.get(minColIndex).fitLine();
        GLine2D colMaxLine = colDetections.get(maxColIndex).fitLine();

        double rowVanishingPoint[] = rowMinLine.intersectionWith(rowMaxLine);
        double colVanishingPoint[] = colMinLine.intersectionWith(colMaxLine);

        //System.out.printf("Vanishing points (%7.1f, %7.1f) and (%7.1f, %7.1f)\n",
        //                  rowVanishingPoint[0], rowVanishingPoint[1],
        //                  colVanishingPoint[0], colVanishingPoint[1]);

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

    ////////////////////////////////////////////////////////////////////////////////
    // TagMosaic helper class
    ////////////////////////////////////////////////////////////////////////////////

    private class TagMosaic
    {
        // IntrinsicsEstimator does not require tagPositions and metersPerTag is not given
        // ArrayList<double[]> tagPositions;

        ArrayList<int[]>    tagRowCol;

        public TagMosaic(TagFamily tf)
        {
            tagRowCol = new ArrayList<int[]>();

            // TODO Add a method to TagFamily that returns this grid?
            int mosaicWidth     = (int) Math.sqrt(tf.codes.length);
            int mosaicHeight    = tf.codes.length / mosaicWidth + 1;

            for (int y=0; y < mosaicHeight; y++) {
                for (int x=0; x < mosaicWidth; x++) {
                    int id = y*mosaicWidth + x;
                    if (id >= tf.codes.length)
                        continue;

                    // IntrinsicsEstimator does not require tagPositions and metersPerTag is not given
                    // tagPositions.add(new double[] { x * metersPerTag ,
                    //                                 y * metersPerTag ,
                    //                                 0.0              });

                    tagRowCol.add(new int[] { y, x });
                }
            }
        }

        public class GroupedDetections
        {
            public final static int ROW_GROUP = 0;
            public final static int COL_GROUP = 1;
            public int type;

            // row or column index in the tag mosaic
            public int index;

            // list of detections in this row or column
            public ArrayList<TagDetection> detections;

            public GLine2D fitLine()
            {
                ArrayList<double[]> centers = new ArrayList<double[]>();
                for (TagDetection d : detections)
                    centers.add(d.cxy);

                return GLine2D.lsqFit(centers);
            }
        }

        public ArrayList<GroupedDetections> getRowDetections(ArrayList<TagDetection> detections)
        {
            return getGroupedDetections(detections, GroupedDetections.ROW_GROUP);
        }

        public ArrayList<GroupedDetections> getColumnDetections(ArrayList<TagDetection> detections)
        {
            return getGroupedDetections(detections, GroupedDetections.COL_GROUP);
        }

        public ArrayList<GroupedDetections> getGroupedDetections(ArrayList<TagDetection> detections,
                                                                 int groupType)
        {
            HashMap<Integer,ArrayList<TagDetection>> groupLists = new HashMap<Integer,ArrayList<TagDetection>>();

            for (int i=0; i < detections.size(); i++) {
                TagDetection d = detections.get(i);

                int rowcol[] = tagRowCol.get(d.id);
                int group = rowcol[groupType]; // the row or column number

                ArrayList<TagDetection> groupList = groupLists.get(group);
                if (groupList == null)
                    groupList = new ArrayList<TagDetection>();

                groupList.add(d);
                groupLists.put(group, groupList);
            }

            Set<Integer> groupKeys = groupLists.keySet();

            ArrayList<GroupedDetections> groups = new ArrayList<GroupedDetections>();
            for (Integer group : groupKeys) {

                GroupedDetections gd = new GroupedDetections();
                gd.type = groupType;
                gd.index = group;
                gd.detections = groupLists.get(group);

                groups.add(gd);
            }

            return groups;
        }
    }
}
