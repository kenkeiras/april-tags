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
    private TagFamily tf;
    private int width;
    private int height;

    public IntrinsicsEstimator2(List<List<TagDetection>> allDetections, TagFamily tf,
                                double fallbackcx, double fallbackcy)
    {
        this.mosaic = new TagMosaic(tf);
        this.tf = tf;

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
            K = CameraMath.estimateIntrinsicsFromOneVanishingPointWithGivenCxCy(vanishingPoints.get(0), fallbackcx, fallbackcy);
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
            xy_rcs.add(new double[] { mosaic.getRow(d.id), mosaic.getCol(d.id) });
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

    ////////////////////////////////////////////////////////////////////////////////
    // TagMosaic helper class
    ////////////////////////////////////////////////////////////////////////////////

    private class TagMosaic
    {
        // IntrinsicsEstimator2 does not require tagPositions and metersPerTag is not given
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

                    // IntrinsicsEstimator2 does not require tagPositions and metersPerTag is not given
                    // tagPositions.add(new double[] { x * metersPerTag ,
                    //                                 y * metersPerTag ,
                    //                                 0.0              });

                    tagRowCol.add(new int[] { y, x });
                }
            }
        }

        public int getRow(long id)
        {
            return tagRowCol.get((int) id)[0];
        }

        public int getCol(long id)
        {
            return tagRowCol.get((int) id)[1];
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

        public ArrayList<GroupedDetections> getRowDetections(List<TagDetection> detections)
        {
            return getGroupedDetections(detections, GroupedDetections.ROW_GROUP);
        }

        public ArrayList<GroupedDetections> getColumnDetections(List<TagDetection> detections)
        {
            return getGroupedDetections(detections, GroupedDetections.COL_GROUP);
        }

        public ArrayList<GroupedDetections> getGroupedDetections(List<TagDetection> detections,
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

