package april.tag;

import java.awt.image.BufferedImage;
import java.util.*;

import april.jmat.geom.GLine2D;

public class TagMosaic
{
    List<double[]> tagPositionsMeters = new ArrayList<double[]>();
    List<double[]> tagPositionsPixels = new ArrayList<double[]>();

    // x=column, y=row
    List<int[]> tagColumnAndRow = new ArrayList<int[]>();

    TagFamily tf;
    double tagSpacingMeters;

    int mosaicWidth, mosaicHeight;
    int tagWidthPixels, tagHeightPixels;

    BufferedImage mosaicImage;

    public TagMosaic(TagFamily tf, double tagSpacingMeters)
    {
        this.tf = tf;
        this.tagSpacingMeters = tagSpacingMeters;

        tagWidthPixels  = tf.d + tf.whiteBorder*2 + tf.blackBorder*2;
        tagHeightPixels = tagWidthPixels;

        mosaicWidth     = (int) Math.sqrt(tf.codes.length);
        mosaicHeight    = tf.codes.length / mosaicWidth + 1;

        for (int row = 0; row < mosaicHeight; row++) {
            for (int col = 0; col < mosaicWidth; col++) {

                int id = row*mosaicWidth + col;

                if (id >= tf.codes.length)
                    continue;

                tagPositionsMeters.add(new double[] { col * tagSpacingMeters ,
                                                      row * tagSpacingMeters ,
                                                      0                      });

                tagPositionsPixels.add(new double[] { tagWidthPixels  * (0.5 + col) ,
                                                      tagHeightPixels * (0.5 + row) ,
                                                      0                             });

                tagColumnAndRow.add(new int[] { col, row });

                assert(tagPositionsMeters.size() == id+1);
                assert(tagPositionsPixels.size() == id+1);
                assert(tagColumnAndRow.size() == id+1);
            }
        }

        assert(tagPositionsMeters.size() == tf.codes.length);
        assert(tagPositionsPixels.size() == tf.codes.length);
        assert(tagColumnAndRow.size() == tf.codes.length);
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Utility functions

    /** Get the column for a tag.
      */
    public int getColumn(int id)
    {
        int colrow[] = tagColumnAndRow.get(id);
        return colrow[0];
    }

    /** Get the row for a tag.
      */
    public int getRow(int id)
    {
        int colrow[] = tagColumnAndRow.get(id);
        return colrow[1];
    }

    /** Get the position of this tag on the tag mosaic image. Positions
      * are relative to the top left corner of tag zero in pixels.
      */
    public double[] getPositionPixels(int id)
    {
        return tagPositionsPixels.get(id);
    }

    /** Get the position of this tag on the physical tag mosaic. Positions are
      * measured from the center of tag zero in meters.
      */
    public double[] getPositionMeters(int id)
    {
        return tagPositionsMeters.get(id);
    }

    /** Get the image of the whole mosaic (from the TagFamily).
      */
    public BufferedImage getImage()
    {
        if (mosaicImage == null)
            mosaicImage = tf.getAllImagesMosaic();

        return mosaicImage;
    }

    /** Get the image of a single tag (from the TagFamily).
      */
    public BufferedImage getImage(int id)
    {
        return tf.makeImage(id);
    }

    ////////////////////////////////////////
    // size functions

    public int getMosaicWidth()
    {
        return this.mosaicWidth;
    }

    public int getMosaicHeight()
    {
        return this.mosaicHeight;
    }

    public int getTagWidthPixels()
    {
        return this.tagWidthPixels;
    }

    public int getTagHeightPixels()
    {
        return this.tagHeightPixels;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Group actual tag detections into row and column groups

    public class GroupedDetections
    {
        public final static int COL_GROUP = 0;
        public final static int ROW_GROUP = 1;
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

    /** Sort the tag detections into groups by row.
      */
    public ArrayList<GroupedDetections> getRowDetections(List<TagDetection> detections)
    {
        return getGroupedDetections(detections, GroupedDetections.ROW_GROUP);
    }

    /** Sort the tag detections into groups by column.
      */
    public ArrayList<GroupedDetections> getColumnDetections(List<TagDetection> detections)
    {
        return getGroupedDetections(detections, GroupedDetections.COL_GROUP);
    }

    private ArrayList<GroupedDetections> getGroupedDetections(List<TagDetection> detections,
                                                              int groupType)
    {
        HashMap<Integer,ArrayList<TagDetection>> groupLists = new HashMap<Integer,ArrayList<TagDetection>>();

        for (int i=0; i < detections.size(); i++) {
            TagDetection d = detections.get(i);

            int colrow[] = tagColumnAndRow.get(d.id);
            int group = colrow[groupType]; // the row or column number

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
