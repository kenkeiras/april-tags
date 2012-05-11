package april.camera.calibrator;

import java.awt.image.*;
import java.util.*;

import april.camera.*;
import april.config.*;
import april.graph.*;
import april.jmat.*;
import april.jmat.geom.*;
import april.jmat.ordering.*;
import april.tag.*;
import april.util.*;
import april.vis.*;

/** Camera calibrator class. Takes a list of full-length class names (e.g.
 * april.camera.CaltechCalibration) an AprilTag family for tag detection,
 * and (optionally) a VisWorld for debugging. Once instantiated, add sets of
 * images (one image per camera) with the addImages method.
 */
public class CameraCalibrator
{
    List<String>                classnames  = null;
    List<List<BufferedImage>>   images      = new ArrayList<List<BufferedImage>>();

    List<CameraWrapper>         cameras     = null;

    TagDetector detector;
    TagFamily   tf;
    double      metersPerTag;

    VisWorld        vw;
    VisLayer        vl;
    VisCanvas       vc;
    VisWorld.Buffer vb;

    Graph g;
    GraphSolver solver;

    ArrayList<double[]> tagPositions;

    ArrayList<TagConstraint> constraints = new ArrayList<TagConstraint>();
    ArrayList<Integer> mosaicExtrinsicsIndices = new ArrayList<Integer>();

    double Tvis[][] = new double[][] { {  0,  0,  1,  0 },
                                       { -1,  0,  0,  0 } ,
                                       {  0, -1,  0,  0 } ,
                                       {  0,  0,  0,  1 } };

    int counter = 0;

    private static class CameraWrapper
    {
        public ParameterizableCalibration cal;

        public GIntrinsicsNode cameraIntrinsics;
        public int cameraIntrinsicsIndex;

        public GExtrinsicsNode cameraExtrinsics;
        public int cameraExtrinsicsIndex;
        public boolean extrinsicsInitialized;

        String name;
        String classname;

    }

    private static class TagConstraint
    {
        public int      tagid;
        public int      graphNodeIndex;

        public int      cameraIndex;
        public int      imageIndex;

        public double   xy_px[];
        public double   xyz_m[];
    }

    /** The constructor for the somewhat-general camera calibrator.  This
     * calibrator has been tested on single cameras and stereo camera pairs.
     *
     * @param classnames - A list of classes (one per camera) that specify
     * which Calibration class to use for each camera (e.g.
     * SimpleCaltechCalibration)
     * @param tf - The family of AprilTags (in april.tag) used on the tag
     * mosaic, e.g. Tag36h11.
     * @param metersPerTag - The spacing on the tag mosaic. This is <b>not</b>
     * the width of the tag -- it is the distance between a point on one tag
     * (e.g. top left corner) to the same point on the adjacent tag.
     * Effectively, the tag width including part of the white border.
     */
    public CameraCalibrator(List<String> classnames, TagFamily tf,
                            double metersPerTag)
    {
        this(classnames, tf, metersPerTag, null);
    }

    /** The constructor for the somewhat-general camera calibrator.  This
     * calibrator has been tested on single cameras and stereo camera pairs.
     *
     * @param classnames - A list of classes (one per camera) that specify
     * which Calibration class to use for each camera (e.g.
     * SimpleCaltechCalibration)
     * @param tf - The family of AprilTags (in april.tag) used on the tag
     * mosaic, e.g. Tag36h11.
     * @param metersPerTag - The spacing on the tag mosaic. This is <b>not</b>
     * the width of the tag -- it is the distance between a point on one tag
     * (e.g. top left corner) to the same point on the adjacent tag.
     * Effectively, the tag width including part of the white border.
     * @param vl - VisLayer for plotting the current state of the graph. This
     * includes 3D rig positions (drawn with VzAxes) and VzRectangles for each
     * position of the tag mosaic
     */
    public CameraCalibrator(List<String> classnames, TagFamily tf,
                            double metersPerTag, VisLayer vl)
    {
        this.classnames = classnames;

        this.tf = tf;
        this.detector = new TagDetector(this.tf);
        this.metersPerTag = metersPerTag;

        this.vl = vl;
        this.vw = vl.world;
        ((DefaultCameraManager) vl.cameraManager).interfaceMode = 3.0;
        vl.cameraManager.uiLookAt(new double[] { 1.5, 0.03, 4.75000 },
                                  new double[] { 1.5, 0.03, 0.00000 },
                                  new double[] { 1.0, 0.00, 0.00000 },
                                  true);
        vl.cameraManager.getCameraTarget().perspectiveness = 0;
        VzGrid.addGrid(vw);

        g = new Graph();
        CholeskySolver gs = new CholeskySolver(g, new MinimumDegreeOrdering());
        gs.verbose = false;
        solver = gs;

        generateTagPositions(tf, metersPerTag);
    }

    private void generateTagPositions(TagFamily tf, double metersPerTag)
    {
        tagPositions = new ArrayList<double[]>();

        // TODO Add a method to TagFamily that returns this grid?
        int mosaicWidth     = (int) Math.sqrt(tf.codes.length);
        int mosaicHeight    = tf.codes.length / mosaicWidth + 1;

        for (int y=0; y < mosaicHeight; y++) {
            for (int x=0; x < mosaicWidth; x++) {
                int id = y*mosaicWidth + x;
                if (id >= tf.codes.length)
                    continue;

                tagPositions.add(new double[] { x * metersPerTag ,
                                                y * metersPerTag ,
                                                0.0              });
            }
        }
    }

    /** Add sets of images, <b>exactly one image per camera</b>, for
     * calibration. The mosaic and cameras must not move within the list of
     * images provided. <b>addImages</b> should be called multiple times (at
     * least three) no matter how many cameras are present.
     */
    public synchronized void addImages(List<BufferedImage> newImages)
    {
        if (cameras == null)
            initCameras(newImages);

        assert(newImages.size() == cameras.size());

        ArrayList<ArrayList<TagDetection>> allDetections = new ArrayList<ArrayList<TagDetection>>();
        for (int cameraIndex = 0; cameraIndex < newImages.size(); cameraIndex++) {

            BufferedImage im = newImages.get(cameraIndex);

            ArrayList<TagDetection> detections = detector.process(im, new double[] {im.getWidth()/2, im.getHeight()/2});
            allDetections.add(detections);

            // skip if we don't have a reasonable number of observed tags?
            // XXX is this the right thing to do? should we reject the whole image set?
            if (detections.size() < 8)
                return;
        }

        this.images.add(newImages);
        int imagesetIndex = this.images.size() - 1;

        GExtrinsicsNode mosaicExtrinsics = new GExtrinsicsNode();
        g.nodes.add(mosaicExtrinsics);
        System.out.printf("Added mosaic extrinsics. Graph contains %d nodes\n", g.nodes.size());
        int mosaicIndex = g.nodes.size() - 1;
        mosaicExtrinsicsIndices.add(mosaicIndex);
        assert(mosaicExtrinsicsIndices.size() == this.images.size());

        for (int cameraIndex = 0; cameraIndex < newImages.size(); cameraIndex++) {

            ArrayList<TagDetection> detections = allDetections.get(cameraIndex);

            CameraWrapper camera = cameras.get(cameraIndex);

            if (cameraIndex == 0) {
                double mosaicToGlobal_est[][] = estimateMosaicExtrinsics(detections);
                // XXX update mosaic extrinsics estimate in a cleaner way
                mosaicExtrinsics.init = LinAlg.matrixToXyzrpy(mosaicToGlobal_est);
                mosaicExtrinsics.state = LinAlg.copy(mosaicExtrinsics.init);

            } else if (!camera.extrinsicsInitialized) {

                double mosaicToCamera[][] = estimateMosaicExtrinsics(detections);

                // should been initialized by camera 0
                double mosaicToGlobal[][] = LinAlg.xyzrpyToMatrix(mosaicExtrinsics.state);

                double cameraToGlobal[][] = LinAlg.matrixAB(mosaicToGlobal,
                                                            LinAlg.inverse(mosaicToCamera));

                GExtrinsicsNode camExtrinsics = camera.cameraExtrinsics;
                camExtrinsics.init = LinAlg.matrixToXyzrpy(cameraToGlobal);
                camExtrinsics.state = LinAlg.copy(camExtrinsics.init);

                camera.extrinsicsInitialized = true;
            }

            processImage(cameraIndex,
                         imagesetIndex,
                         mosaicIndex,
                         detections);
        }

        draw();
    }

    public void draw()
    {
        // don't try to draw until we have a few images
        if (images.size() < 3)
            return;

        vb = vw.getBuffer("Extrinsics - cameras");
        for (CameraWrapper cam : cameras) {

            double B2G[][] = null;

            if (cam.cameraExtrinsics != null)
                B2G = LinAlg.xyzrpyToMatrix(cam.cameraExtrinsics.state);
            else
                B2G = LinAlg.identity(4);


            vb.addBack(new VisChain(Tvis,
                                    B2G,
                                    LinAlg.scale(0.05, 0.05, 0.05),
                                    new VzAxes()));
        }
        vb.swap();

        double XY0[] = null;
        double XY1[] = null;

        for (TagConstraint tc : constraints) {

            if (XY0 == null && XY1 == null) {
                XY0 = new double[2];
                XY0[0] = tc.xyz_m[0];
                XY0[1] = tc.xyz_m[1];

                XY1 = new double[2];
                XY1[0] = tc.xyz_m[0];
                XY1[1] = tc.xyz_m[1];

                continue;
            }

            XY0[0] = Math.min(XY0[0], tc.xyz_m[0]);
            XY0[1] = Math.min(XY0[1], tc.xyz_m[1]);
            XY1[0] = Math.max(XY1[0], tc.xyz_m[0]);
            XY1[1] = Math.max(XY1[1], tc.xyz_m[1]);
        }

        vb = vw.getBuffer("Extrinsics - mosaics");
        for (int index : mosaicExtrinsicsIndices) {
            GNode node = g.nodes.get(index);

            double B2G[][] = LinAlg.xyzrpyToMatrix(node.state);

            java.awt.Color c = ColorUtil.seededColor(index);

            vb.addBack(new VisChain(Tvis,
                                    B2G,
                                    LinAlg.translate((XY0[0]+XY1[0])/2.0, (XY0[1]+XY1[1])/2.0, 0),
                                    new VzRectangle(XY1[0] - XY0[0],
                                                    XY1[1] - XY0[1],
                                                    new VzLines.Style(c, 2))));
        }
        vb.swap();

        vb = vw.getBuffer("HUD");
        double reprojError = 0;
        int numObs = 0;
        for (GEdge e : g.edges) {
            assert(e instanceof GTagEdge);
            GTagEdge edge = (GTagEdge) e;

            double res[] = edge.getResidualExternal(g);
            assert((res.length & 0x1) == 0);

            int len = res.length / 2;
            for (int i=0; i < len; i+=2)
                reprojError += Math.sqrt(res[i]*res[i] + res[i+1]*res[i+1]);
            numObs += len;
        }
        vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.TOP_RIGHT,
                                    new VzText(VzText.ANCHOR.TOP_RIGHT,
                       String.format("<<monospaced-14,white>>Mean reprojection error: %8.6f pixels",
                                     reprojError / numObs))));
        vb.swap();
    }

    public synchronized void iterate()
    {
        // don't try to optimize until we have a few images
        if (images.size() < 3)
            return;

        solver.iterate();

        if ((counter % 100) == 0)
            printCalibrationBlock();
        counter++;
    }

    public void printCalibrationBlock()
    {
        // start block
        System.out.println("cameraCalibration {\n");

        // print name list
        String names = "    names = [";
        for (int i=0; i+1 < cameras.size(); i++) {
            CameraWrapper cam = cameras.get(i);
            names = String.format("%s %s,", names, cam.name);
        }
        names = String.format("%s %s ];", names, cameras.get(cameras.size()-1).name);
        System.out.println(names);

        // print cameras
        for (int i=0; i < cameras.size(); i++) {

            CameraWrapper cam = cameras.get(i);

            System.out.println();
            System.out.printf("    %s {\n", cam.name);

            System.out.println(cam.cal.getCalibrationString());

            double state[] = new double[6];
            if (cam.cameraExtrinsics != null) {
                state = LinAlg.copy(g.nodes.get(cam.cameraExtrinsicsIndex).state);
                double C2L[][] = LinAlg.xyzrpyToMatrix(state);
                double L2C[][] = LinAlg.inverse(C2L);
                state = LinAlg.matrixToXyzrpy(L2C);
            }

            String s;
            s = String.format(  "        extrinsics {\n");
            s = String.format("%s            // Global-To-Camera coordinate transformation\n", s);
            s = String.format("%s            position = [%11.6f,%11.6f,%11.6f ];\n", s, state[0], state[1], state[2]);
            s = String.format("%s            rollpitchyaw_degrees = [%11.6f,%11.6f,%11.6f ];\n",
                              s, state[3]*180/Math.PI, state[4]*180/Math.PI, state[5]*180/Math.PI);
            s = String.format("%s        }\n", s);
            System.out.printf("%s", s);

            System.out.printf("    }\n");
        }

        // end block
        System.out.println("}");
    }

    private void initCameras(List<BufferedImage> newImages)
    {
        assert(newImages.size() == classnames.size());

        cameras = new ArrayList<CameraWrapper>();

        for (int i=0; i < classnames.size(); i++) {

            BufferedImage im = newImages.get(i);

            String classname = classnames.get(i);

            String name     = String.format("camera%d", i);
            ParameterizableCalibration cal = getDefaultCalibration(classname, im.getWidth(), im.getHeight());

            GIntrinsicsNode cameraIntrinsics = new GIntrinsicsNode(cal);
            System.out.println("Initialized camera intrinsics to:"); LinAlg.print(cameraIntrinsics.init);
            g.nodes.add(cameraIntrinsics);
            System.out.printf("Added camera intrinsics. Graph contains %d nodes\n", g.nodes.size());
            int cameraIntrinsicsIndex = g.nodes.size() - 1;

            GExtrinsicsNode cameraExtrinsics = null;
            int cameraExtrinsicsIndex        = -1;
            if (i != 0) {
                cameraExtrinsics = new GExtrinsicsNode(LinAlg.identity(4));
                g.nodes.add(cameraExtrinsics);
                System.out.printf("Added camera extrinsics. Graph contains %d nodes\n",
                                  g.nodes.size());
                cameraExtrinsicsIndex = g.nodes.size() - 1;
            }

            CameraWrapper camera = new CameraWrapper();
            camera.cal                      = cal;
            camera.cameraIntrinsics         = cameraIntrinsics;
            camera.cameraIntrinsicsIndex    = cameraIntrinsicsIndex;
            camera.cameraExtrinsics         = cameraExtrinsics;
            camera.cameraExtrinsicsIndex    = cameraExtrinsicsIndex;
            camera.extrinsicsInitialized    = false;
            camera.name                     = name;
            camera.classname                = classname;

            cameras.add(camera);
        }

        assert(cameras.size() == newImages.size());
    }

    private ParameterizableCalibration getDefaultCalibration(String classname, int width, int height)
    {
        if (classname.equals("april.camera.CaltechCalibration")) {

            // XXX cheating
            //double fc[] = new double[] { 650, 650 };
            //double cc[] = new double[] { width/2, height/2 };
            //double kc[] = new double[] { -.4, .2, 0, 0, 0 };
            double fc[] = new double[] { 500, 500 };
            double cc[] = new double[] { width/2, height/2 };
            double kc[] = new double[] { 0, 0, 0, 0, 0 };
            double skew = 0;

            return new CaltechCalibration(fc, cc, kc, skew, width, height);
        }

        if (classname.equals("april.camera.SimpleCaltechCalibration")) {

            // XXX cheating
            //double fc[] = new double[] { 650, 650 };
            //double cc[] = new double[] { width/2, height/2 };
            //double kc[] = new double[] { -.4, .2 };

            double fc[] = new double[] { 500, 500 };
            double cc[] = new double[] { width/2, height/2 };
            double kc[] = new double[] { 0, 0 };

            return new SimpleCaltechCalibration(fc, cc, kc, width, height);
        }

        assert(false);
        return null;
    }

    private double[][] estimateMosaicExtrinsics(ArrayList<TagDetection> detections)
    {
        // XXX better estimates?
        double fx_est = 500;
        double fy_est = 500;

        ArrayList<double[]> points_camera_est = new ArrayList<double[]>();
        ArrayList<double[]> points_mosaic = new ArrayList<double[]>();

        for (int i = 0; i < detections.size(); i++) {

            TagDetection d = detections.get(i);

            double tagToCamera_est[][] = CameraUtil.homographyToPose(fx_est, fy_est,
                                                                     metersPerTag,
                                                                     d.homography);
            double tagPosition_est[] = LinAlg.select(LinAlg.matrixToXyzrpy(tagToCamera_est),
                                                     0, 2);
            // XXX convert to X-right, Y-down, Z-in coordinates
            tagPosition_est = new double[] { tagPosition_est[0] ,
                                            -tagPosition_est[1] ,
                                            -tagPosition_est[2] };

            points_camera_est.add(tagPosition_est);

            double xyz_m[] = LinAlg.copy(tagPositions.get(d.id));
            points_mosaic.add(xyz_m);
        }

        double mosaicToGlobal_est[][] = AlignPoints3D.align(points_mosaic,
                                                            points_camera_est);
        System.out.println("Estimated mosaic extrinsics:");
        LinAlg.printTranspose(LinAlg.matrixToXyzrpy(mosaicToGlobal_est));

        return mosaicToGlobal_est;
    }

    private void processImage(int cameraIndex, int imageIndex, int mosaicIndex,
                              ArrayList<TagDetection> detections)
    {
        // index where edge will appear (though it's not there now)
        int tagEdgeIndex = g.edges.size();

        ArrayList<double[]> xys_px = new ArrayList<double[]>();
        ArrayList<double[]> xyzs_m = new ArrayList<double[]>();

        for (int i = 0; i < detections.size(); i++) {

            TagDetection d = detections.get(i);

            double xy_px[] = LinAlg.copy(d.cxy);
            double xyz_m[] = LinAlg.copy(tagPositions.get(d.id));

            xys_px.add(xy_px);
            xyzs_m.add(xyz_m);

            TagConstraint constraint = new TagConstraint();
            constraint.tagid            = d.id;
            constraint.graphNodeIndex   = tagEdgeIndex;
            constraint.cameraIndex      = cameraIndex;
            constraint.imageIndex       = imageIndex;
            constraint.xy_px            = LinAlg.copy(xy_px);
            constraint.xyz_m            = LinAlg.copy(xyz_m);

            constraints.add(constraint);

            System.out.printf("Constraining (%8.3f, %8.3f) to (%8.3f, %8.3f, %8.3f)\n",
                              constraint.xy_px[0], constraint.xy_px[1],
                              constraint.xyz_m[0], constraint.xyz_m[1], constraint.xyz_m[2]);
        }

        CameraWrapper cam = cameras.get(cameraIndex);
        GTagEdge edge = new GTagEdge(cam.cameraIntrinsicsIndex,
                                     cam.cameraExtrinsicsIndex,
                                     mosaicIndex,
                                     xys_px,
                                     xyzs_m);
        g.edges.add(edge);
        System.out.printf("Added tag edge. Graph contains %d edges\n", g.edges.size());
        assert(g.edges.size()-1 == tagEdgeIndex);

        printStatus();
    }

    void printStatus()
    {
        int nodeDOF = 0;
        for (GNode n : g.nodes)
            nodeDOF += n.getDOF();

        int edgeDOF = 0;
        for (GEdge e : g.edges)
            edgeDOF += e.getDOF();

        System.out.printf("Graph has %d nodes and %d edges.\n",
                          g.nodes.size(), g.edges.size());
        System.out.printf("There are %d camera wrappers, %d mosaics, and %d image sets\n",
                          cameras.size(), mosaicExtrinsicsIndices.size(), images.size());
        System.out.printf("Total node DOF is %d. Total edge DOF is %d\n",
                          nodeDOF, edgeDOF);
    }
}
