package april.camera.calibrator;

import java.awt.Color;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.imageio.*;

import april.camera.*;
import april.camera.models.*;
import april.config.*;
import april.graph.*;
import april.jmat.*;
import april.jmat.geom.*;
import april.jmat.ordering.*;
import april.tag.*;
import april.util.*;
import april.vis.*;

/** Camera calibrator class. Takes a list of full-length class names (e.g.
 * april.camera.CaltechCalibration), an AprilTag family for tag detection, and
 * the distance between tags on the calibration mosaic (see constructor for
 * details). It also takes two optional parameters: a VisLayer for debugging
 * and a list of initial parameter values for the camera intrinsics. Once
 * instantiated, add sets of images (one image per camera) with the addImages
 * method.
 */
public class CameraCalibrator
{
    public int REQUIRED_NUM_IMAGES = 3;       // number of images needed before iteration
    public int REQUIRED_TAGS_PER_IMAGE = 8;   // number of constraints needed per image
    public static boolean verbose = true;

    List<CalibrationInitializer> initializers       = null;
    List<double[]>               initialParameters  = null;
    List<double[]>               initialExtrinsics  = null;
    List<List<ProcessedImage>>   images             = new ArrayList<List<ProcessedImage>>();
    List<double[]>               mosaicExtInits     = new ArrayList<double[]>();

    List<CameraWrapper>          cameras            = null;

    TagDetector detector;
    TagFamily   tf;
    TagMosaic   mosaic;
    double      metersPerTag;

    VisWorld        vw;
    VisLayer        vl;
    VisCanvas       vc;
    VisWorld.Buffer vb;
    TreeSet<String> disabledBuffers = new TreeSet<String>();

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

    private static class ProcessedImage
    {
        public BufferedImage image;
        public List<TagDetection> detections;

        public ProcessedImage(BufferedImage image, List<TagDetection> detections)
        {
            this.image = image;
            this.detections = detections;
        }
    }

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
     * Camera intrinsics are initialized to sane default values.
     */
    public CameraCalibrator(List<CalibrationInitializer> initializers, TagFamily tf,
                            double metersPerTag)
    {
        this(initializers, tf, metersPerTag, null);
    }

    public CameraCalibrator(List<CalibrationInitializer> initializers,
                            TagFamily tf, double metersPerTag,
                            VisLayer vl)
    {
        this(initializers, tf, metersPerTag, vl, true);
    }

    /** The constructor for the somewhat-general camera calibrator.  This
     * calibrator has been tested on single cameras and stereo camera pairs.
     *
     * @param initializers - A list of CalibrationInitializers (one per camera)
     * that will be used to determine the initial parameter values for each camera
     * intrinsics
     * @param tf - The family of AprilTags (in april.tag) used on the tag
     * mosaic, e.g. Tag36h11.
     * @param metersPerTag - The spacing on the tag mosaic. This is <b>not</b>
     * the width of the tag -- it is the distance between a point on one tag
     * (e.g. top left corner) to the same point on the adjacent tag.
     * Effectively, the tag width including part of the white border.
     * @param vl - (Optional) VisLayer for plotting the current state of the graph. This
     * includes 3D rig positions (drawn with VzAxes) and VzRectangles for each
     * position of the tag mosaic
     */
    public CameraCalibrator(List<CalibrationInitializer> initializers,
                            TagFamily tf, double metersPerTag,
                            VisLayer vl, boolean resetVisCameraManager)
    {
        this.initializers = initializers;

        this.tf = tf;
        this.detector = new TagDetector(this.tf);
        this.metersPerTag = metersPerTag;
        this.mosaic = new TagMosaic(tf, metersPerTag);

        this.vl = vl;
        if (vl != null) {
            this.vw = vl.world;

            if (resetVisCameraManager) {
                DefaultCameraManager cameraManager = (DefaultCameraManager) vl.cameraManager;
                cameraManager.interfaceMode = 2.5;

                VisCameraManager.CameraPosition pos = cameraManager.getCameraTarget();
                pos.eye    = new double[] { 0.1, 0.0, 2.0 };
                pos.lookat = new double[] { 0.1, 0.0, 0.0 };
                pos.up     = new double[] { 1.0, 0.0, 0.0 };
                pos.perspectiveness = 0;
                //pos.eye    = new double[] { 1.2, 0.0, 0.5 };
                //pos.lookat = new double[] { 0.2, 0.0, 0.0 };
                //pos.up     = new double[] {-0.4, 0.0, 0.9 };
                cameraManager.goUI(pos);
                cameraManager.setDefaultPosition(pos.eye, pos.lookat, pos.up);

                VzGrid.addGrid(vw, new VzGrid(new VzLines.Style(new Color(128, 128, 128, 128), 1)));
                vw.getBuffer("grid").setDrawOrder(-10001);
                vw.getBuffer("grid-overlay").setDrawOrder(-10000);
            }
        }

        g = new Graph();

        CholeskySolver gs   = new CholeskySolver(g, new MinimumDegreeOrdering());
        //LMSolver gs = new LMSolver(g, new MinimumDegreeOrdering(), 10e-1, 10e10, 10);
        gs.verbose          = false;
        gs.matrixType       = Matrix.SPARSE;
        solver = gs;

        generateTagPositions(tf, metersPerTag);
    }

    /** Set the camera intrinsics for each camera. If the cameras haven't been
      * initialized, we save this setting for later. Otherwise, we apply it now
      * and edit the underlying graph.
      */
    public synchronized void setCalibrationParameters(List<double[]> parameters)
    {
        assert(parameters.size() == initializers.size());

        // prepare the parameters for initialization when we call addImages()
        // and return
        if (cameras == null) {
            initialParameters = parameters;
            return;
        }

        // reset each camera's intrinsics
        for (int i=0; i < cameras.size(); i++) {
            CameraWrapper cam = cameras.get(i);

            double params[] = parameters.get(i);

            cam.cal.resetParameterization(params);
            cam.cameraIntrinsics.state = LinAlg.copy(params);
        }
    }

    /** Set the extrinsic parameters for each camera relative to the first camera.
      * If the cameras haven't been initialized, we'll save this state for later.
      * Otherwise, we apply it now and edit the underlying graph.
      *
      * @param xyzrpys - An XYZRPY double for each camera <b>except</b> the
      * first camera which is "privileged" and located at the origin. Be sure
      * to specify the "camera to global" transformation.
      */
    public synchronized void setRelativeExtrinsicsCameraToGlobal(List<double[]> xyzrpys)
    {
        assert(xyzrpys.size() == (initializers.size() - 1));

        // prepare the extrinsics for the first call to addImages()
        // and return;
        if (cameras == null) {
            initialExtrinsics = xyzrpys;
            return;
        }

        // reset each camera's intrinsics
        for (int i=0; i < xyzrpys.size(); i++) {

            double xyzrpy[] = xyzrpys.get(i);

            int cameraIndex = i + 1;
            CameraWrapper cam = cameras.get(cameraIndex);
            cam.cameraExtrinsics.state = LinAlg.copy(xyzrpy);
        }
    }

    /** Return list of double-vectors for all cameras using the getParameterization method of
     * the ParameterizableCalibration interface.
     */
    public synchronized List<double[]> getCalibrationParameters()
    {
        ArrayList<double[]> parameters = new ArrayList<double[]>();

        for (CameraWrapper cam : cameras)
            parameters.add(cam.cal.getParameterization());

        return parameters;
    }

    /** Return vector of doubles for this camera using the getParameterization method of
     * the ParameterizableCalibration interface.
     */
    public synchronized double[] getCalibrationParameters(int cameraIndex)
    {
        if (cameras == null)
            return null;

        if (cameraIndex < 0 || cameraIndex >= cameras.size())
            return null;

        CameraWrapper cam = cameras.get(cameraIndex);
        return cam.cal.getParameterization();
    }

    /** Copy and return the intrinsics for the specified camera.
      */
    public synchronized double[][] getCalibrationIntrinsics(int cameraIndex)
    {
        if (cameras == null)
            return null;

        if (cameraIndex < 0 || cameraIndex >= cameras.size())
            return null;

        CameraWrapper cam = cameras.get(cameraIndex);
        return cam.cal.copyIntrinsics();
    }

    /** Return the XYZRPY camera-to-global representation of the extrinsics
      * for the specified camera.
      */
    public synchronized double[] getCalibrationExtrinsics(int cameraIndex)
    {
        assert(cameraIndex >= 0 && cameraIndex < cameras.size());

        if (cameraIndex == 0)
            return new double[6];

        CameraWrapper cam = cameras.get(cameraIndex);

        return LinAlg.copy(cam.cameraExtrinsics.state);
    }

    public synchronized double[] getMosaicExtrinsics(int mosaic)
    {
        return LinAlg.copy(g.nodes.get(mosaicExtrinsicsIndices.get(mosaic)).state);
    }

    public synchronized ArrayList<double[]> getMosaicExtrinsics()
    {
        ArrayList<double[]> extrins = new ArrayList();

        for (Integer i : mosaicExtrinsicsIndices)
            extrins.add(LinAlg.copy(g.nodes.get(i).state));

        return extrins;
    }

    public synchronized Graph getGraphCopy()
    {
        return g.copy();
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

    public synchronized void addImages(List<BufferedImage> newImages)
    {
        addImages(newImages, null, null);
    }

    public synchronized void addImages(List<BufferedImage> newImages,
                                       List<List<TagDetection>> allDetections)
    {
        addImages(newImages, allDetections, null);
    }

    public synchronized void addImages(List<BufferedImage> newImages,
                                       double MosaicToGlobalXyzrpy[])
    {
        addImages(newImages, null, MosaicToGlobalXyzrpy);
    }

    /** Add sets of images, <b>exactly one image per camera</b>, for
     * calibration in the same order as initialization.
     * If allDetections is null, the april.tag.TagDetector will detect tags in
     * the images. If it is not null, the provided TagDetections will be used.
     * If MosaicToGlobalXyzrpy is null, the position will be estimated with
     * CameraUtil.homographyToPose and jmat.AlignPoints3D. If it is not null,
     * the provided estimate will be used.<br>
     * <br>
     * The mosaic and cameras must not move within the list of images provided.
     */

    public synchronized void addImages(List<BufferedImage> newImages,
                                       List<List<TagDetection>> allDetections,
                                       double MosaicToGlobalXyzrpy[])
    {
        boolean success = addImagesNoInit(newImages, allDetections, MosaicToGlobalXyzrpy);

        if (success)
            initialize();
    }

    // Delays initialization until all images have been processed.
    public synchronized void addImageSet(List<List<BufferedImage>> newImagesSet,
                                         List<List<List<TagDetection>>> allDetectionsSet,
                                         List<double[]> MosaicToGlobalXyzrpySet )
    {
        assert(newImagesSet.size() == allDetectionsSet.size());
        assert(allDetectionsSet.size() == MosaicToGlobalXyzrpySet.size());

        boolean success = false;
        for (int i = 0; i < newImagesSet.size(); i++) {
            success |= addImagesNoInit(newImagesSet.get(i), allDetectionsSet.get(i), MosaicToGlobalXyzrpySet.get(i));
        }

        if (success)
            initialize();
    }

    private synchronized boolean addImagesNoInit(List<BufferedImage> newImages,
                                                 List<List<TagDetection>> allDetections,
                                                 double MosaicToGlobalXyzrpy[])
    {
        assert(newImages.size() == initializers.size());
        assert((allDetections == null) || (allDetections.size() == initializers.size()));

        ArrayList<ProcessedImage> processedImages = new ArrayList<ProcessedImage>();
        for (int cameraIndex = 0; cameraIndex < newImages.size(); cameraIndex++) {

            BufferedImage im = newImages.get(cameraIndex);

            // if we've already gotten a set of images, enforce that all images
            // from a camera have the same size
            if (this.images.size() > 0) {
                List<ProcessedImage> firstImages = this.images.get(0);
                ProcessedImage reference = firstImages.get(cameraIndex);
                assert(reference.image.getWidth() == im.getWidth());
                assert(reference.image.getHeight() == im.getHeight());
            }

            List<TagDetection> detections = null;

            if (allDetections == null)
                detections = detector.process(im, new double[] {im.getWidth()/2, im.getHeight()/2});
            else
                detections = allDetections.get(cameraIndex);

            // skip if we don't have a reasonable number of observed tags?
            // XXX is this the right thing to do? should we reject the whole image set?
            if (detections.size() < REQUIRED_TAGS_PER_IMAGE)
                return false;

            processedImages.add(new ProcessedImage(im, detections));
        }

        this.images.add(processedImages);
        this.mosaicExtInits.add(MosaicToGlobalXyzrpy);

        return true;
    }

    private void initialize() {

        // initialize the cameras if we haven't provided we have the minimum number of images
        if (cameras == null) {
            ArrayList<CameraWrapper> cameraList = initCameras();

            // if we didn't succeed at initializing this time, try again later
            if (cameraList == null)
                return;
            // else
            cameras = cameraList;

            // add all the images we've collected so far
            //XXX This reuses the extrinsics for /this/ mosaic to redo
            //XXX all the previous ones in the case of delayed
            //XXX initialization
            for (int i=0; i < this.images.size(); i++)
                addImageSet(this.images.get(i), i, this.mosaicExtInits.get(i));
        }
        // otherwise, we just need to add the new set of images
        else {
            int idx = this.images.size() - 1;
            addImageSet(this.images.get(idx), idx , this.mosaicExtInits.get(idx));
        }
    }

    private void addImageSet(List<ProcessedImage> newImages, int imagesetIndex, double MosaicToGlobalXyzrpy[])
    {
        GExtrinsicsNode mosaicExtrinsics = new GExtrinsicsNode();
        g.nodes.add(mosaicExtrinsics);
        if (verbose) System.out.printf("Added mosaic extrinsics. Graph contains %d nodes\n", g.nodes.size());
        int mosaicIndex = g.nodes.size() - 1;
        mosaicExtrinsicsIndices.add(mosaicIndex);
        assert(mosaicExtrinsicsIndices.size() == imagesetIndex + 1);

        for (int cameraIndex = 0; cameraIndex < newImages.size(); cameraIndex++) {

            ProcessedImage pim = newImages.get(cameraIndex);
            CameraWrapper camera = cameras.get(cameraIndex);

            if (cameraIndex == 0) {

                if (MosaicToGlobalXyzrpy == null) {
                    double mosaicToGlobal_est[][] = estimateMosaicExtrinsics(camera.cal.copyIntrinsics(),
                                                                             pim.detections);

                    MosaicToGlobalXyzrpy = LinAlg.matrixToXyzrpy(mosaicToGlobal_est);
                }

                mosaicExtrinsics.init = LinAlg.copy(MosaicToGlobalXyzrpy);
                mosaicExtrinsics.state = LinAlg.copy(mosaicExtrinsics.init);

            } else if (!camera.extrinsicsInitialized) {

                GExtrinsicsNode camExtrinsics = camera.cameraExtrinsics;

                // Initialize camera extrinsics with the provided values
                if (initialExtrinsics != null) {
                    double xyzrpy[] = initialExtrinsics.get(cameraIndex);
                    camExtrinsics.init = LinAlg.copy(xyzrpy);

                // Initialize camera extrinsics with our best guess
                } else {
                    double mosaicToCamera[][] = estimateMosaicExtrinsics(camera.cal.copyIntrinsics(),
                                                                         pim.detections);

                    // should been initialized by camera 0
                    double mosaicToGlobal[][] = LinAlg.xyzrpyToMatrix(mosaicExtrinsics.state);

                    double cameraToGlobal[][] = LinAlg.matrixAB(mosaicToGlobal,
                                                                LinAlg.inverse(mosaicToCamera));

                    camExtrinsics.init = LinAlg.matrixToXyzrpy(cameraToGlobal);
                }

                camExtrinsics.state = LinAlg.copy(camExtrinsics.init);
                camera.extrinsicsInitialized = true;
            }

            processImage(cameraIndex,
                         imagesetIndex,
                         mosaicIndex,
                         pim.detections);
        }

        if (imagesetIndex+1 == this.images.size())
            draw();
    }

    public synchronized void draw()
    {
        if (vl == null)
            return;

        // nothing can be drawn prior to the initialization
        if (cameras == null)
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
        vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.TOP_RIGHT,
                                    new VzText(VzText.ANCHOR.TOP_RIGHT,
                       String.format("<<monospaced-14,right,white>>Mean reprojection error: %8.6f pixels\nMean square reprojection error: %8.6f pixels",
                                     getMRE(), getMSE()))));
        vb.swap();

        ////////////////////////////////////////
        vb = vw.getBuffer("Distortion");

        VisChain bgchain = new VisChain();
        VisChain mgchain = new VisChain();
        VisChain fgchain = new VisChain();

        for (int idx = 0; idx < cameras.size(); idx++) {

            CameraWrapper wrapper = cameras.get(idx);
            View cal = wrapper.cal;

            double K[][] = cal.copyIntrinsics();
            double Kinv[][] = LinAlg.inverse(K);
            int width = cal.getWidth();
            int height = cal.getHeight();

            double maxRadius = 0;

            // top left
            {
                double radius = Math.sqrt(Math.pow(       0 - K[0][2] , 2)
                                        + Math.pow(       0 - K[1][2] , 2));
                maxRadius = Math.max(maxRadius, radius);
            }

            // top right
            {
                double radius = Math.sqrt(Math.pow( width-1 - K[0][2] , 2)
                                        + Math.pow(       0 - K[1][2] , 2));
                maxRadius = Math.max(maxRadius, radius);
            }

            // bottom left
            {
                double radius = Math.sqrt(Math.pow(       0 - K[0][2] , 2)
                                        + Math.pow(height-1 - K[1][2] , 2));
                maxRadius = Math.max(maxRadius, radius);
            }

            // bottom right
            {
                double radius = Math.sqrt(Math.pow( width-1 - K[0][2] , 2)
                                        + Math.pow(height-1 - K[1][2] , 2));
                maxRadius = Math.max(maxRadius, radius);
            }

            ArrayList<double[]> points = new ArrayList<double[]>();
            int radius = (int) Math.ceil(2 * maxRadius); // XXX is this far enough?
            double max = 0;
            for (int r=0; r < radius; r++) {
                double xy_rp[] = new double[] { K[0][2] + r, K[1][2] };

                double xy_rn[] = CameraMath.pixelTransform(Kinv, xy_rp);

                double xy_dp[] = cal.normToPixels(xy_rn);

                double dr = xy_dp[0] - K[0][2];

                points.add(new double[] { r, dr });

                max = Math.max(max, dr);
            }

            boolean good = max > maxRadius;
            String uistring = String.format("<<monospaced-10,%s>>%s: %s\n<<monospaced-10,%s>>Defined up to %.0f of %.0f pixels",
                                            good ? "white" : "red",
                                            wrapper.name,
                                            good ? "good (invertible)" : "add tags at edges",
                                            good ? "white" : "red",
                                            max,
                                            maxRadius);

            bgchain.add(new VisChain(LinAlg.translate(100, 50 + 100*idx, 0),
                                     new VzRectangle(200, 100,
                                                     new VzMesh.Style(new Color(10*idx, 10*idx, 10*idx)),
                                                     new VzLines.Style(Color.white, 1))),
                        new VisChain(LinAlg.translate(0, 100*idx, 0),
                                     new VzLines(new VisVertexData(new double[][] { {  0,   0},
                                                                                    {100, 100} }),
                                                 VzLines.LINE_STRIP,
                                                 new VzLines.Style(new Color(255, 255, 255, 128), 1))));
            mgchain.add(new VisChain(LinAlg.translate(0, 100*idx, 0),
                                     LinAlg.scale(100 / maxRadius,
                                                  100 / maxRadius,
                                                  1),
                                     new VzLines(new VisVertexData(points),
                                                 VzLines.LINE_STRIP,
                                                 new VzLines.Style(Color.red, 1))));
            fgchain.add(new VisChain(LinAlg.translate(35, 100*idx, 0),
                                     new VzText(VzText.ANCHOR.BOTTOM_LEFT, "<<monospaced-10>>r rect")),
                        new VisChain(LinAlg.translate(0, 10+100*idx, 0),
                                     LinAlg.rotateZ(Math.PI/2),
                                     new VzText(VzText.ANCHOR.TOP_LEFT, "<<monospaced-10>>r dist")),
                        new VisChain(LinAlg.translate(0, 100*(idx+1), 0),
                                     new VzText(VzText.ANCHOR.TOP_LEFT, uistring)));
        }

        vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_LEFT,
                                    bgchain,
                                    mgchain,
                                    fgchain));
        vb.swap();

        ////////////////////////////////////////
        vb = vw.getBuffer("Sample density");

        for (int cameraIndex = 0; cameraIndex < cameras.size(); cameraIndex++) {

            int width  = images.get(0).get(cameraIndex).image.getWidth();
            int height = images.get(0).get(cameraIndex).image.getHeight();

            int w = width / 30 + 2;
            int h = height / 30;
            int hist[] = new int[w*h];

            for (int imageSetIndex=0; imageSetIndex < images.size(); imageSetIndex++) {
                List<TagDetection> detections = images.get(imageSetIndex).get(cameraIndex).detections;

                for (TagDetection d : detections) {
                    int x = (int) (d.cxy[0] / 30);
                    int y = (int) (d.cxy[1] / 30);
                    hist[y*w+x+2]++;
                }
            }

            int max = 0;
            for (int i=0; i < hist.length; i++)
                max = Math.max(max, hist[i]);

            for (int y=0; y < h; y++) {
                int percent = ((h-1-y) * (max+1)) / h;
                hist[y*w+0] = percent;
            }

            BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            int buf[] = ((DataBufferInt) (im.getRaster().getDataBuffer())).getData();
            for (int y=0; y < h; y++) {
                for (int x=0; x < w; x++) {
                    int i=y*w+x;
                    int v = hist[i];

                    if (x == 1) {
                        buf[i] = 0;
                    }
                    else if (v == 0) {
                        buf[i] = 0xFF00FF00;
                    }
                    else {
                        int b = (int) (v * 255 / max);
                        buf[i] = 0xFF000000 | (b & 0xFF);
                    }
                }
            }

            VisTexture texture = new VisTexture(im, VisTexture.NO_MIN_FILTER |
                                                    VisTexture.NO_MAG_FILTER |
                                                    VisTexture.NO_REPEAT |
                                                    VisTexture.NO_ALPHA_MASK);

            double scale = 150.0/w;
            vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_RIGHT,
                                        new VisChain(LinAlg.translate(-150, 150*h/w*cameraIndex, 0),
                                                     LinAlg.scale(scale, scale, 1),
                                                     new VzImage(texture, VzImage.FLIP))));
        }

        vb.swap();

        ///////////// Reprojection Histogram
        vb = vw.getBuffer("reprojection-hist");
        {
            ArrayList<Double> errs = new ArrayList();
            for (GEdge e : g.edges) {
                assert(e instanceof GTagEdge);
                GTagEdge edge = (GTagEdge) e;

                double res[] = edge.getResidualExternal(g);
                assert((res.length & 0x1) == 0);

                int len = res.length / 2;
                for (int i=0; i < len; i+=2)
                    errs.add(Math.sqrt(res[i]*res[i] + res[i+1]*res[i+1]));
            }

            // Compute histogram of errs.
            Collections.sort(errs);

            double eMin = errs.get(0);
            double eMax = errs.get(errs.size() -1);

            int nbuckets = 90;
            double bucket = (eMax - eMin) / nbuckets;

            double thresh = eMin + bucket;
            ArrayList<Integer> counts = new ArrayList();
            int ct = 0;
            int maxCt = 0, maxIdx = -1;
            for (Double e : errs) {

                while (e > thresh) {
                    thresh += bucket;
                    if (ct > maxCt) {
                        maxCt = ct;
                        maxIdx = counts.size();
                    }
                    counts.add(ct);
                    ct = 0;
                }
                ct++;
            }
            while(counts.size() < nbuckets)
                counts.add(0);

            double maxHist = (maxIdx + 0.5)*bucket + eMin;

            // Draw histogram:
            int height = 100 - 2*22;//3*counts.size()/2;
            BufferedImage hist = new BufferedImage(counts.size(), height, BufferedImage.TYPE_INT_ARGB);

            int x = 0;
            for (Integer val : counts) {
                for (int y = 0; y < height; y++) {

                    int ymax = (int)Math.ceil(height*val*1.0/maxCt);

                    if (ymax != 0 && y <= ymax) {
                        hist.setRGB(x,y, 0xffffffff);
                    } else {
                        hist.setRGB(x,y, 0x00ffffff);
                    }
                }
                x++;
            }
            vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM,
                                        new VzText(VzText.ANCHOR.BOTTOM, String.format("<<white>>MRE [%.3f, %.3f]", eMin,eMax)),
                                        LinAlg.translate(-counts.size()/2,22,0),
                                        new VzImage(new VisTexture(hist, VisTexture.NO_MAG_FILTER | VisTexture.NO_MIN_FILTER),0),
                                        LinAlg.translate(maxIdx,height,0),
                                        new VzText(VzText.ANCHOR.BOTTOM_LEFT,String.format("peak %.3f",maxHist))));

        }

        vb.swap();

        ////////////////////////////////////////
        // calibration text
        String calstr = getCalibrationBlockString();
        vb = vw.getBuffer("Calibration");
        vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.TOP_LEFT,
                                    new VzText(VzText.ANCHOR.TOP_LEFT,
                                               "<<dropshadow=#AA000000>>"+
                                               "<<sansserif-9,white>>"+
                                               calstr)));
        vb.swap();

        ////////////////////////////////////////
        // reprojected images

        for (int cameraIndex = 0; cameraIndex < cameras.size(); cameraIndex++) {

            for (int imageSetIndex=0; imageSetIndex < images.size(); imageSetIndex++) {

                VisChain chain = new VisChain();

                ProcessedImage pim = images.get(imageSetIndex).get(cameraIndex);
                int width = pim.image.getWidth();
                int height = pim.image.getHeight();

                String buffername = String.format("Camera%d-Image%d", cameraIndex, imageSetIndex);
                if (!disabledBuffers.contains(buffername)) {
                    disabledBuffers.add(buffername);
                    vl.setBufferEnabled(buffername, false);
                }

                vb = vw.getBuffer(buffername);
                VzImage vzim = new VzImage(new VisTexture(pim.image, VisTexture.NO_MIN_FILTER|
                                                                     VisTexture.NO_MAG_FILTER|
                                                                     VisTexture.NO_ALPHA_MASK|
                                                                     VisTexture.NO_REPEAT),
                                           0);
                chain.add(LinAlg.translate(0, height, 0));
                chain.add(LinAlg.scale(1, -1, 1));
                chain.add(vzim);

                CameraWrapper camera = cameras.get(cameraIndex);
                GExtrinsicsNode mosaicExtrinsics = (GExtrinsicsNode) g.nodes.get(mosaicExtrinsicsIndices.get(imageSetIndex));

                double[][] cameraToGlobal = LinAlg.identity(4);
                if (camera.cameraExtrinsics != null)
                    cameraToGlobal = camera.cameraExtrinsics.getMatrix();

                double[][] mosaicToGlobal = mosaicExtrinsics.getMatrix();

                double[][] mosaicToCamera = LinAlg.matrixAB(LinAlg.inverse(cameraToGlobal),
                                                            mosaicToGlobal);

                double tagWidth_m = metersPerTag * (tf.d/2.0 + tf.blackBorder) / (tf.d + 2*tf.blackBorder + 2*tf.whiteBorder);

                for (TagDetection d : pim.detections) {

                    ////////////////////////////////////////
                    // predicted position
                    {
                        double xyz_mosaic[] = tagPositions.get(d.id);
                        double xyz_camera[] = LinAlg.transform(mosaicToCamera, xyz_mosaic);
                        double xy_predicted[] = camera.cameraIntrinsics.project(xyz_camera);
                        chain.add(new VzPoints(new VisVertexData(xy_predicted),
                                               new VzPoints.Style(Color.cyan, 3)));
                    }

                    double border[][] = new double[4][];
                    {
                        double xyz_mosaic[] = LinAlg.copy(tagPositions.get(d.id));
                        xyz_mosaic[0] += tagWidth_m;
                        xyz_mosaic[1] += tagWidth_m;

                        double xyz_camera[] = LinAlg.transform(mosaicToCamera, xyz_mosaic);
                        double xy_predicted[] = camera.cameraIntrinsics.project(xyz_camera);
                        border[0] = xy_predicted;
                    }
                    {
                        double xyz_mosaic[] = LinAlg.copy(tagPositions.get(d.id));
                        xyz_mosaic[0] += tagWidth_m;
                        xyz_mosaic[1] -= tagWidth_m;

                        double xyz_camera[] = LinAlg.transform(mosaicToCamera, xyz_mosaic);
                        double xy_predicted[] = camera.cameraIntrinsics.project(xyz_camera);
                        border[1] = xy_predicted;
                    }
                    {
                        double xyz_mosaic[] = LinAlg.copy(tagPositions.get(d.id));
                        xyz_mosaic[0] -= tagWidth_m;
                        xyz_mosaic[1] -= tagWidth_m;

                        double xyz_camera[] = LinAlg.transform(mosaicToCamera, xyz_mosaic);
                        double xy_predicted[] = camera.cameraIntrinsics.project(xyz_camera);
                        border[2] = xy_predicted;
                    }
                    {
                        double xyz_mosaic[] = LinAlg.copy(tagPositions.get(d.id));
                        xyz_mosaic[0] -= tagWidth_m;
                        xyz_mosaic[1] += tagWidth_m;

                        double xyz_camera[] = LinAlg.transform(mosaicToCamera, xyz_mosaic);
                        double xy_predicted[] = camera.cameraIntrinsics.project(xyz_camera);
                        border[3] = xy_predicted;
                    }
                    chain.add(new VzLines(new VisVertexData(border),
                                          VzLines.LINE_LOOP,
                                          new VzLines.Style(Color.cyan, 2)));

                    ////////////////////////////////////////
                    // observed position
                    double p0[] = d.interpolate(-1,-1);
                    double p1[] = d.interpolate(1,-1);
                    double p2[] = d.interpolate(1,1);
                    double p3[] = d.interpolate(-1,1);

                    double ymax = Math.max(Math.max(p0[1], p1[1]), Math.max(p2[1], p3[1]));

                    chain.add(new VzPoints(new VisVertexData(d.cxy),
                                           new VzPoints.Style(Color.blue, 3)));
                    chain.add(new VzLines(new VisVertexData(p0, p1, p2, p3, p0),
                                          VzLines.LINE_STRIP,
                                          new VzLines.Style(Color.blue, 1)));
                    chain.add(new VzLines(new VisVertexData(p0,p1),
                                          VzLines.LINE_STRIP,
                                          new VzLines.Style(Color.green, 1)));
                    chain.add(new VzLines(new VisVertexData(p0, p3),
                                          VzLines.LINE_STRIP,
                                          new VzLines.Style(Color.red, 1)));
                }

                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                            new VisChain(LinAlg.translate(-width/2, -height/2, 0),
                                                         chain)));

                vb.swap();
            }
        }

    }

    public synchronized void iterate()
    {
        // don't try to optimize until we have a few images
        // XXX replace this later with an rank check of the graph
        if (images.size() < REQUIRED_NUM_IMAGES)
            return;

        solver.iterate();

        if ((counter % 100) == 0) {

            if (verbose) printCalibrationBlock();

            //int numTagEdges = 0;
            //for (GEdge e : g.edges)
            //    if (e instanceof GTagEdge)
            //        numTagEdges++;

            //System.out.printf("Time data:\n");
            //System.out.printf("%s %12.6f seconds\n",
            //                  "    Linearization            ",
            //                  1.0e-9 * numTagEdges * GTagEdge.time_linearize / GTagEdge.time_number_of_linearizations);
            //System.out.printf("%s %12.6f seconds\n",
            //                  "        Residual             ",
            //                  1.0e-9 * numTagEdges * GTagEdge.time_residual / GTagEdge.time_number_of_linearizations);
            //System.out.printf("%s %12.6f seconds\n",
            //                  "        Jacobian (intrinsics)",
            //                  1.0e-9 * numTagEdges * GTagEdge.time_jacobian_intrinsics / GTagEdge.time_number_of_linearizations);
            //System.out.printf("%s %12.6f seconds\n",
            //                  "        Jacobian (extrinsics)",
            //                  1.0e-9 * numTagEdges * GTagEdge.time_jacobian_extrinsics / GTagEdge.time_number_of_linearizations);
            //System.out.printf("%s %12.6f seconds\n",
            //                  "        Jacobian (mosaics)   ",
            //                  1.0e-9 * numTagEdges * GTagEdge.time_jacobian_mosaics / GTagEdge.time_number_of_linearizations);
        }
        counter++;
    }

    // Returns mean reprojections error
    public synchronized double getMRE()
    {
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
        return reprojError / numObs;
    }

    public synchronized double getMSE()
    {
        double sqError = 0;
        int numObs = 0;
        for (GEdge e : g.edges) {
            assert(e instanceof GTagEdge);
            GTagEdge edge = (GTagEdge) e;

            double res[] = edge.getResidualExternal(g);
            assert((res.length & 0x1) == 0);

            int len = res.length / 2;
            for (int i=0; i < len; i+=2)
                sqError += res[i]*res[i] + res[i+1]*res[i+1];

            numObs += len;
        }
        return sqError / numObs;
    }

    public void printCalibrationBlock()
    {
        if (cameras == null || cameras.size() < 1)
            return;

        System.out.printf(getCalibrationBlockString());
    }

    private String getCalibrationBlockString()
    {
        if (cameras == null || cameras.size() == 0)
            return null;

        String str = "";

        // start block
        str += "aprilCameraCalibration {\n";
        str += "\n";

        // Comment about MRE, MSE for this calibration
        if (true) {
            str += String.format("    // MRE: %.5f\n",getMRE());
            str += String.format("    // MSE: %.5f\n",getMSE());
            str += "\n";
        }

        // print name list
        String names = "    names = [";
        for (int i=0; i+1 < cameras.size(); i++) {
            CameraWrapper cam = cameras.get(i);
            names = String.format("%s %s,", names, cam.name);
        }
        names = String.format("%s %s ];\n", names, cameras.get(cameras.size()-1).name);
        str += names;

        // print cameras
        for (int i=0; i < cameras.size(); i++) {

            CameraWrapper cam = cameras.get(i);

            str += "\n";
            str += String.format("    %s {\n", cam.name);

            // make sure ParameterizableCalibration is up to date and print it
            cam.cal.resetParameterization(cam.cameraIntrinsics.state);
            str += cam.cal.getCalibrationString();

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

            str += s;
            str += "    }\n";
        }

        // end block
        str += "}\n";

        return str;
    }

    public void saveDetections()
    {
        saveDetections("/tmp/CameraCalibration");
    }

    public void saveDetections(String basepath)
    {
        if (images == null || images.size() < 1 || cameras == null)
            return;

        String dirName = String.format("%s/", basepath);
        File dir = new File(dirName);

        if (dir.exists() && !dir.isDirectory()) {
            System.err.printf("CameraCalibrator: File exists at the desired directory '%s'\n", dirName);
            return;
        }

        if (!dir.exists()) {
            boolean mkdir = dir.mkdirs();
            if (mkdir != true) {
                System.err.printf("CameraCalibrator: Failed to create directory '%s'\n", dirName);
                return;
            }
        }

        try {
            String filename = String.format("%s/detections.csv", basepath);
            BufferedWriter outs = new BufferedWriter(new FileWriter(new File(filename)));

            for (int imageSetIndex = 0; imageSetIndex < images.size(); imageSetIndex++) {
                List<ProcessedImage> imageSet = images.get(imageSetIndex);

                for (int cameraIndex = 0; cameraIndex < imageSet.size(); cameraIndex++) {

                    ProcessedImage pim = imageSet.get(cameraIndex);

                    CameraWrapper cam = cameras.get(cameraIndex);
                    double K[][] = cam.cal.copyIntrinsics();

                    for (TagDetection d : pim.detections)
                        outs.write(String.format("%12.6f, %12.6f\n", d.cxy[0], d.cxy[1]));
                }
            }

            outs.close();

            System.out.println("Saved detections successfully.");

        } catch (IOException ex) {
            System.out.println("Error saving detections: " + ex);
        }
    }

    public void saveImages()
    {
        saveImages("/tmp/CameraCalibration");
    }

    public void saveImages(String basepath)
    {
        if (images == null || images.size() < 1)
            return;

        // create directory for image dump
        int dirNum = -1;
        String dirName = null;
        File dir = null;
        do {
            dirNum++;
            dirName = String.format("%s/ImageSet%d/", basepath, dirNum);
            dir = new File(dirName);
        } while (dir.exists());

        if (dir.mkdirs() != true) {
            System.err.printf("CameraCalibrator: Failure to create directory '%s'\n", dirName);
            return;
        }


        // dump images
        for (int imageSetIndex = 0; imageSetIndex < images.size(); imageSetIndex++) {

            List<ProcessedImage> imageSet = images.get(imageSetIndex);

            // save images
            for (int cameraIndex = 0; cameraIndex < imageSet.size(); cameraIndex++) {
                ProcessedImage pim = imageSet.get(cameraIndex);

                String fileName = String.format("%s/Camera%d_Image%d.png", dirName, cameraIndex, imageSetIndex);
                File imageFile = new File(fileName);

                System.out.printf("Filename '%s'\n", fileName);

                try {
                    ImageIO.write(pim.image, "png", imageFile);

                } catch (IllegalArgumentException ex) {
                    System.err.printf("CameraCalibrator: Failure to output images to %s\n", dirName);
                    return;
                } catch (IOException ex) {
                    System.err.printf("CameraCalibrator: Failure to output images to %s\n", dirName);
                    return;
                }
            }
        }

        System.out.printf("Successfully saved images to '%s'\n", dirName);
    }

    private ArrayList<CameraWrapper> initCameras()
    {
        // get all of the images available for each camera
        ArrayList<ArrayList<ProcessedImage>> imagesForEachCamera = new ArrayList<ArrayList<ProcessedImage>>();
        for (int i=0; i < initializers.size(); i++) {

            ArrayList<ProcessedImage> currentCameraImages = new ArrayList<ProcessedImage>();
            for (int j=0; j < images.size(); j++)
                currentCameraImages.add(images.get(j).get(i));

            imagesForEachCamera.add(currentCameraImages);
        }

        ArrayList<ParameterizableCalibration> initializations = new ArrayList<ParameterizableCalibration>();
        for (int i=0; i < initializers.size(); i++) {

            CalibrationInitializer initializer = initializers.get(i);
            assert(initializer != null);

            ArrayList<ProcessedImage> currentCameraImages = imagesForEachCamera.get(i);
            int width = currentCameraImages.get(0).image.getWidth();
            int height = currentCameraImages.get(0).image.getHeight();

            // get all the detections (one set per image) for this camera for
            // the purposes of initialization
            List<List<TagDetection>> allDetections = new ArrayList<List<TagDetection>>();
            for (ProcessedImage pim : currentCameraImages)
                allDetections.add(pim.detections);

            ParameterizableCalibration cal = null;
            if (initialParameters != null)
                // if specified, initialize the camera parameters to the provided values
                cal = initializer.initializeWithParameters(width, height,
                                                           initialParameters.get(i));
            else
                // normally, let the initializer proceed as it sees fit
                cal = initializer.initializeWithObservations(width, height,
                                                             allDetections, this.mosaic);

            // if the initializer failed, we probably don't have enough images
            // and will try again next time
            if (cal == null)
                return null;

            initializations.add(cal);
        }

        ArrayList<CameraWrapper> cameraList = new ArrayList<CameraWrapper>();
        for (int i=0; i < initializers.size(); i++) {

            String name = String.format("camera%d", i);
            ParameterizableCalibration cal = initializations.get(i);

            // create graph intrinsics node
            GIntrinsicsNode cameraIntrinsics = new GIntrinsicsNode(cal);
            if (verbose) System.out.println("Initialized camera intrinsics to:");
            if (verbose) LinAlg.print(cameraIntrinsics.init);
            g.nodes.add(cameraIntrinsics);
            if (verbose) System.out.printf("Added camera intrinsics. Graph contains %d nodes\n", g.nodes.size());
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
            camera.classname                = cal.getClass().getName();

            cameraList.add(camera);
        }

        // if all cameras were initialized successfully, we're ready to hand off
        // the list of camera wrappers
        assert(cameraList.size() == initializers.size());
        return cameraList;
    }

    private double[][] estimateMosaicExtrinsics(double K[][], List<TagDetection> detections)
    {
        /* // Old, per-tag homography decomposition and AlignPoints3D
        ArrayList<double[]> points_camera_est = new ArrayList<double[]>();
        ArrayList<double[]> points_mosaic = new ArrayList<double[]>();

        for (int i = 0; i < detections.size(); i++) {

            TagDetection d = detections.get(i);

            double tagToCamera_est[][] = CameraUtil.homographyToPose(K[0][0], K[1][1],
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
        if (verbose) System.out.println("Estimated mosaic extrinsics:");
        if (verbose) LinAlg.printTranspose(LinAlg.matrixToXyzrpy(mosaicToGlobal_est));

        return mosaicToGlobal_est;
        */

        // Fit a homography jointly to all of the tag observations and
        // decompose it to estimate the extrinsics
        ArrayList<double[]> points_mosaic_meters = new ArrayList<double[]>();
        ArrayList<double[]> points_image_pixels = new ArrayList<double[]>();

        for (TagDetection d : detections) {
            points_mosaic_meters.add(LinAlg.select(tagPositions.get(d.id), 0, 1));
            points_image_pixels.add(LinAlg.select(d.cxy, 0, 1));
        }

        double H[][] = CameraMath.estimateHomography(points_mosaic_meters,
                                                     points_image_pixels);

        double Rt[][] = CameraMath.decomposeHomography(H, K, points_mosaic_meters.get(0));

        return Rt;
    }

    private void processImage(int cameraIndex, int imageIndex, int mosaicIndex,
                              List<TagDetection> detections)
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

            // System.out.printf("Constraining (%8.3f, %8.3f) to (%8.3f, %8.3f, %8.3f)\n",
            //                   constraint.xy_px[0], constraint.xy_px[1],
            //                   constraint.xyz_m[0], constraint.xyz_m[1], constraint.xyz_m[2]);
        }

        CameraWrapper cam = cameras.get(cameraIndex);
        GTagEdge edge = new GTagEdge(cam.cameraIntrinsicsIndex,
                                     cam.cameraExtrinsicsIndex,
                                     mosaicIndex,
                                     xys_px,
                                     xyzs_m);
        g.edges.add(edge);
        if (verbose) System.out.printf("Added tag edge. Graph contains %d edges\n", g.edges.size());
        assert(g.edges.size()-1 == tagEdgeIndex);

        if (verbose) printStatus();
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
