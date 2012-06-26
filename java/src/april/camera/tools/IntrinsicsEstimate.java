package april.camera.tools;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import javax.swing.*;

import april.camera.*;
import april.config.*;
import april.jcam.*;
import april.jmat.*;
import april.jmat.geom.*;
import april.tag.*;
import april.util.*;
import april.vis.*;

public class IntrinsicsEstimate implements ParameterListener
{
    ArrayList<String> paths = new ArrayList<String>();
    ArrayList<BufferedImage> images = new ArrayList<BufferedImage>();
    ArrayList<ArrayList<TagDetection>> allDetections = new ArrayList<ArrayList<TagDetection>>();

    ArrayList<Integer> goodIndices = new ArrayList<Integer>();
    ArrayList<ArrayList<double[][]>> allFitLines = new ArrayList<ArrayList<double[][]>>();
    ArrayList<double[][]> vanishingPoints = new ArrayList<double[][]>();

    ArrayList<int[]> tagRowCol;   // the row and column for every tag id

    TagFamily tf;
    TagDetector td;

    JFrame          jf;
    VisWorld        vw;
    VisLayer        vl;
    VisCanvas       vc;
    ParameterGUI    pg;
    VisWorld.Buffer vb;

    double K[][];

    boolean once = true;

    public IntrinsicsEstimate(String dirpath, TagFamily tf)
    {
        // check directory
        File dir = new File(dirpath);
        if (!dir.isDirectory()) {
            System.err.println("Not a directory: " + dirpath);
            System.exit(-1);
        }

        // get images
        for (String child : dir.list()) {
            String childpath = dirpath+"/"+child;
            String tmp = childpath.toLowerCase();
            if (tmp.endsWith("jpeg") || tmp.endsWith("jpg") || tmp.endsWith("png") ||
                tmp.endsWith("bmp") || tmp.endsWith("wbmp") || tmp.endsWith("gif"))
                paths.add(childpath);
        }
        Collections.sort(paths);

        try {
            for (String path : paths)
                images.add(ImageIO.read(new File(path)));
        } catch (IOException ex) {
            System.err.println("Exception while loading images: " + ex);
            System.exit(-1);
        }

        // detect tags
        this.tf = tf;
        this.td = new TagDetector(tf);

        for (BufferedImage im : images)
            allDetections.add(td.process(im, new double[] { im.getWidth()/2, im.getHeight()/2 }));

        // generate tag positions
        generateTagPositions(tf);

        // create lines and compute vanishing points
        computeVanishingPoints();

        // setup GUI
        setupGUI();

        // estimate K
        K = CameraMath.estimateIntrinsicsFromVanishingPoints(vanishingPoints);

        if (K == null) {
            System.out.println("Could not estimate intrinsics - K returned from CameraMath is null");
        }
        else {
            System.out.println("Estimated intrinsics matrix K:");
            LinAlg.print(K);
        }

        // for render once
        parameterChanged(pg, "image");
    }

    private void setupGUI()
    {
        pg = new ParameterGUI();
        pg.addIntSlider("image", "Selected image", 0, goodIndices.size()-1, 0);
        pg.addListener(this);

        vw = new VisWorld();
        vl = new VisLayer(vw);
        vc = new VisCanvas(vl);

        jf = new JFrame("Camera intrinsics estimator");
        jf.setLayout(new BorderLayout());

        JSplitPane jspane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, vc, pg);
        jspane.setDividerLocation(1.0);
        jspane.setResizeWeight(1.0);

        jf.add(jspane, BorderLayout.CENTER);
        jf.setSize(1200, 600);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);
    }

    private void generateTagPositions(TagFamily tf)
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

                tagRowCol.add(new int[] { y, x });
            }
        }
    }

    public void parameterChanged(ParameterGUI pg, String name)
    {
        if (name.equals("image"))
            draw();
    }

    private void computeVanishingPoints()
    {
        for (int i=0; i < images.size(); i++) {
            BufferedImage image = images.get(i);
            ArrayList<TagDetection> detections = allDetections.get(i);

            if (detections.size() == 0)
                continue;

            goodIndices.add(i);

            if (detections.size() == 1)
                computeSingleTagVanishingPoints(image, detections.get(0));
            else if (detections.size() > 1)
                computeTagMosaicVanishingPoints(image, detections);
        }
    }

    private void computeSingleTagVanishingPoints(BufferedImage image, TagDetection d)
    {
        int width = image.getWidth();
        int height = image.getHeight();

        // lines on tag
        GLine2D bottom = new GLine2D(d.p[0], d.p[1]);
        GLine2D right  = new GLine2D(d.p[1], d.p[2]);
        GLine2D top    = new GLine2D(d.p[2], d.p[3]);
        GLine2D left   = new GLine2D(d.p[3], d.p[0]);

        // add lines to rendering pool
        ArrayList<double[][]> fitLines = new ArrayList<double[][]>();
        fitLines.add(getLine(width, height, top   ));
        fitLines.add(getLine(width, height, bottom));
        fitLines.add(getLine(width, height, left  ));
        fitLines.add(getLine(width, height, right ));
        allFitLines.add(fitLines);

        // intersect lines to compute vanishing points
        vanishingPoints.add(new double[][] { bottom.intersectionWith(top) ,
                                             left.intersectionWith(right) });
    }

    private void computeTagMosaicVanishingPoints(BufferedImage image, ArrayList<TagDetection> detections)
    {
        int width = image.getWidth();
        int height = image.getHeight();

        HashMap<Integer, ArrayList<TagDetection>> rowLists = new HashMap<Integer, ArrayList<TagDetection>>();
        HashMap<Integer, ArrayList<TagDetection>> colLists = new HashMap<Integer, ArrayList<TagDetection>>();

        ArrayList<double[][]> fitLines = new ArrayList<double[][]>();

        // make lists of detections by mosaic row and column
        for (int i=0; i < detections.size(); i++) {
            TagDetection d = detections.get(i);

            // mosaic positions
            int rowcol[] = tagRowCol.get(d.id);
            int row = rowcol[0];
            int col = rowcol[1];

            ArrayList<TagDetection> rowList = rowLists.get(row);
            if (rowList == null)
                rowList = new ArrayList<TagDetection>();

            ArrayList<TagDetection> colList = colLists.get(col);
            if (colList == null)
                colList = new ArrayList<TagDetection>();

            rowList.add(d);
            colList.add(d);

            rowLists.put(row, rowList);
            colLists.put(col, colList);
        }

        // get observed row keys
        Set<Integer> rows = rowLists.keySet();
        Set<Integer> cols = colLists.keySet();

        // add lines for rows
        for (Integer row : rows) {
            ArrayList<TagDetection> lineDetections = rowLists.get(row);
            if (lineDetections.size() < 2)
                continue;

            ArrayList<double[]> centers = getDetectionCenters(lineDetections);
            GLine2D line = GLine2D.lsqFit(centers);
            fitLines.add(getLine(width, height, line));
        }

        // add lines for cols
        for (Integer col: cols) {
            ArrayList<TagDetection> lineDetections = colLists.get(col);
            if (lineDetections.size() < 2)
                continue;

            ArrayList<double[]> centers = getDetectionCenters(lineDetections);
            GLine2D line = GLine2D.lsqFit(centers);
            fitLines.add(getLine(width, height, line));
        }

        // get min and max row and column for computing vanishing point
        int minRow = Integer.MAX_VALUE;
        int maxRow = Integer.MIN_VALUE;
        int minCol = Integer.MAX_VALUE;
        int maxCol = Integer.MIN_VALUE;

        for (Integer row : rows) {
            if (rowLists.get(row).size() < 2)
                continue;

            minRow = Math.min(minRow, row);
            maxRow = Math.max(maxRow, row);
        }

        for (Integer col : cols) {
            if (colLists.get(col).size() < 2)
                continue;

            minCol = Math.min(minCol, col);
            maxCol = Math.max(maxCol, col);
        }

        double rowVanishingPoint[];
        double colVanishingPoint[];

        // rows
        {
            GLine2D minLine = GLine2D.lsqFit(getDetectionCenters(rowLists.get(minRow)));
            GLine2D maxLine = GLine2D.lsqFit(getDetectionCenters(rowLists.get(maxRow)));

            rowVanishingPoint = minLine.intersectionWith(maxLine);
        }

        // cols
        {
            GLine2D minLine = GLine2D.lsqFit(getDetectionCenters(colLists.get(minCol)));
            GLine2D maxLine = GLine2D.lsqFit(getDetectionCenters(colLists.get(maxCol)));

            colVanishingPoint = minLine.intersectionWith(maxLine);
        }

        vanishingPoints.add(new double[][] { rowVanishingPoint, colVanishingPoint });
        allFitLines.add(fitLines);
    }

    private ArrayList<double[]> getDetectionCenters(ArrayList<TagDetection> lineDetections)
    {
        ArrayList<double[]> centers = new ArrayList<double[]>();
        for (TagDetection d : lineDetections)
            centers.add(d.cxy);

        return centers;
    }

    private double[][] getLine(double width, double height, GLine2D line)
    {
        double p0[] = null;
        double p1[] = null;

        double dx = line.getDx();
        double dy = line.getDy();
        double p[] = line.getPoint();
        double t = line.getTheta();
        t = MathUtil.mod2pi(t);

        double pi = Math.PI;
        // mostly horizontal
        if ((t < -3*pi/4) || (t > -pi/4 && t < pi/4) || (t > 3*pi/4)) {
            p0 = new double[] {     0, p[1] + (    0 - p[0])*dy/dx };
            p1 = new double[] { width, p[1] + (width - p[0])*dy/dx };
        }
        // mostly vertical
        else {
            p0 = new double[] { p[0] + (     0 - p[1])*dx/dy,      0 };
            p1 = new double[] { p[0] + (height - p[1])*dx/dy, height };
        }

        return new double[][] { p0, p1 };
    }

    ////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////

    private void draw()
    {
        int n = pg.gi("image");
        int m = goodIndices.get(n);

        String path                         = paths.get(m);
        BufferedImage image                 = images.get(m);
        ArrayList<TagDetection> detections  = allDetections.get(m);

        ArrayList<double[][]> fitLines      = allFitLines.get(n);
        double vp[][]                       = vanishingPoints.get(n);

        double XY0[] = new double[2];
        double XY1[] = new double[] { image.getWidth(), image.getHeight() };
        double PixelsToVis[][] = CameraMath.makeVisPlottingTransform(image.getWidth(), image.getHeight(),
                                                                     XY0, XY1, true);

        vb = vw.getBuffer("Image");
        vb.addBack(new VisChain(PixelsToVis,
                                new VzImage(image)));
        vb.swap();

        vb = vw.getBuffer("HUD");
        vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.TOP_LEFT,
                                    new VzText(VzText.ANCHOR.TOP_LEFT,
                                               String.format("<<monospaced-12>>%s\n%d detections",
                                                             path, detections.size()))));
        vb.swap();

        vb = vw.getBuffer("Detections");
        for (TagDetection d : detections) {
            double p0[] = d.interpolate(-1,-1);
            double p1[] = d.interpolate( 1,-1);
            double p2[] = d.interpolate( 1, 1);
            double p3[] = d.interpolate(-1, 1);

            vb.addBack(new VisChain(PixelsToVis,
                                    new VzLines(new VisVertexData(p0, p1, p2, p3, p0),
                                                VzLines.LINE_STRIP,
                                                new VzLines.Style(Color.blue, 4)),
                                    new VzLines(new VisVertexData(p0,p1),
                                                VzLines.LINE_STRIP,
                                                new VzLines.Style(Color.green, 4)),
                                    new VzLines(new VisVertexData(p0, p3),
                                                VzLines.LINE_STRIP,
                                                new VzLines.Style(Color.red, 4))));
        }
        vb.swap();

        vb = vw.getBuffer("Fit lines");
        for (double line[][] : fitLines)
            vb.addBack(new VisChain(PixelsToVis,
                                    new VzLines(new VisVertexData(line),
                                                VzLines.LINES,
                                                new VzLines.Style(Color.yellow, 2))));
        vb.addBack(new VisChain(PixelsToVis,
                                new VzPoints(new VisVertexData(vp[0]),
                                             new VzPoints.Style(Color.red, 8))));
        vb.addBack(new VisChain(PixelsToVis,
                                new VzPoints(new VisVertexData(vp[1]),
                                             new VzPoints.Style(Color.green, 8))));
        vb.swap();

        vb = vw.getBuffer("Vanishing lines");
        vb.addBack(new VisChain(PixelsToVis,
                                new VzLines(new VisVertexData(vp),
                                            VzLines.LINE_STRIP,
                                            new VzLines.Style(new Color(170, 100, 10), 1))));
        if (K != null)
            vb.addBack(new VisChain(PixelsToVis,
                                    new VzLines(new VisVertexData(new double[][] { vp[0],
                                                                                   new double[] { K[0][2], K[1][2] },
                                                                                   vp[1] }),
                                                VzLines.LINE_STRIP,
                                                new VzLines.Style(new Color(10, 100, 170), 1))));
        vb.swap();

        if (once) {
            once = false;
            vl.cameraManager.fit2D(XY0, XY1, true);
        }
    }

    public static void main(String args[])
    {
        GetOpt opts = new GetOpt();

        opts.addBoolean('h',"help",false,"See the help screen");
        opts.addString('d',"dir",".","Directory of images containing the AprilTag camera calibration mosaic or a single AprilTag. Accepted formats: jpeg, jpg, png, bmp, wbmp, gif");
        opts.addString('f',"tagfamily","april.tag.Tag36h11","AprilTag family");

        if (!opts.parse(args)) {
            System.out.println("Option error: " + opts.getReason());
        }

        String dir = opts.getString("dir");
        String tagfamily = opts.getString("tagfamily");

        if (opts.getBoolean("help")) {
            System.out.println("Usage:");
            opts.doHelp();
            System.exit(-1);
        }

        TagFamily tf = (TagFamily) ReflectUtil.createObject(tagfamily);

        if (tf == null) {
            System.err.printf("Invalid tag family '%s'\n", tagfamily);
            System.exit(-1);
        }

        new IntrinsicsEstimate(dir, tf);
    }
}
