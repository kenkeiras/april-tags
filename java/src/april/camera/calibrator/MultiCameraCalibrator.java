package april.camera.calibrator;

import java.io.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.image.*;
import java.util.*;

import javax.swing.*;

import april.camera.*;
import april.jcam.*;
import april.tag.*;
import april.util.*;
import april.vis.*;

public class MultiCameraCalibrator implements ParameterListener
{
    JFrame          jf;
    JSplitPane      canvasPane;
    ParameterGUI    pg;

    List<CalibrationInitializer>    initializers;
    String                          urls[];
    ImageSource                     isrcs[];
    ImageSourceFormat               ifmts[];
    BlockingSingleQueue<FrameData>  imageQueues[];

    RobustCameraCalibrator calibrator;
    TagFamily   tf;
    TagDetector td;
    double      metersPerTag;

    VisWorld vwImages;
    VisLayer vlImages;
    VisCanvas vcImages;
    HashMap<Integer,double[][]> PixelsToVisTransforms = new HashMap<Integer,double[][]>();
    List<Color> colorList = new ArrayList<Color>();

    boolean captureOnce = false;

    long start_utime;
    int imageCounter = 0;

    public MultiCameraCalibrator(List<CalibrationInitializer> initializers, String urls[], double metersPerTag)
    {
        this.tf = new Tag36h11();
        this.td = new TagDetector(tf);
        this.initializers = initializers;
        this.metersPerTag = metersPerTag;

        this.urls           = urls;
        this.isrcs          = new ImageSource[urls.length];
        this.ifmts          = new ImageSourceFormat[urls.length];
        this.imageQueues    = new BlockingSingleQueue[urls.length];

        this.start_utime = TimeUtil.utime();

        // Calibrator setup
        calibrator = new RobustCameraCalibrator(initializers, tf, metersPerTag, true);

        // silence!
        CameraCalibrator.verbose = false;
        IntrinsicsEstimator.verbose = false;
        april.camera.models.SimpleKannalaBrandtInitializer.verbose = false;
        april.camera.models.KannalaBrandtInitializer.verbose = false;
        april.camera.models.DistortionFreeInitializer.verbose = false;
        april.camera.models.CaltechInitializer.verbose = false;
        april.camera.models.Radial4thOrderCaltechInitializer.verbose = false;
        april.camera.models.Radial6thOrderCaltechInitializer.verbose = false;
        april.camera.models.Radial8thOrderCaltechInitializer.verbose = false;
        april.camera.models.Radial10thOrderCaltechInitializer.verbose = false;

        pg = new ParameterGUI();
        pg.addCheckBoxes("screenshots","Automatically save screenshots to /tmp", false);
        pg.addButtons("captureOnce","Capture once",
                      "print", "Print calibration block",
                      "savecalibration","Save calibration",
                      "saveall","Save calibration and images");
        pg.addListener(this);

        vwImages = new VisWorld();
        vlImages = new VisLayer(vwImages);
        vcImages = new VisCanvas(vlImages);

        jf = new JFrame("Multi camera calibrator");
        jf.setLayout(new BorderLayout());

        canvasPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                    vcImages,
                                    calibrator.getVisCanvas());
        canvasPane.setDividerLocation(0.3);
        canvasPane.setResizeWeight(0.3);

        JSplitPane jspane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, canvasPane, pg);
        jspane.setDividerLocation(1.0);
        jspane.setResizeWeight(1.0);

        jf.add(jspane, BorderLayout.CENTER);
        jf.setSize(1200, 600);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);

        ////////////////////////////////////////////////////////////////////////////////
        // Camera setup
        for (int i=0; i < urls.length; i++)
        {
            imageQueues[i] = new BlockingSingleQueue<FrameData>();

            try {
                isrcs[i] = ImageSource.make(urls[i]);

                ifmts[i] = isrcs[i].getCurrentFormat();

            } catch (IOException ex) {
                System.err.printf("Exception caught while making image source '%s': %s\n",
                                  urls[i], ex);
                ex.printStackTrace();
                System.exit(-1);
            }

            new AcquisitionThread(urls[i], isrcs[i], imageQueues[i]).start();
        }

        double XY0[] = getXY0(initializers.size()-1);
        double XY1[] = getXY1(0);
        vlImages.cameraManager.fit2D(XY0, XY1, true);

        // get a shuffled set of nice tag colors
        while (colorList.size() < this.tf.codes.length)
        {
            List<Color> colors = Palette.listAll();
            colors.remove(0);
            colorList.addAll(colors);
        }
        Collections.shuffle(colorList, new Random(283819));

        // Threads
        new ProcessingThread().start();

        calibrator.draw();
    }

    public void parameterChanged(ParameterGUI pg, String name)
    {
        if (name.equals("captureOnce")) {
            if (captureOnce) captureOnce = false;
            else             captureOnce = true;
        }

        if (name.equals("print"))
            calibrator.printCalibrationBlock();

        if (name.equals("savecalibration"))
            calibrator.saveCalibration();

        if (name.equals("saveall"))
            calibrator.saveCalibrationAndImages();
    }

    class AcquisitionThread extends Thread
    {
        String url;
        ImageSource isrc;
        BlockingSingleQueue<FrameData> imageQueue;

        public AcquisitionThread(String url, ImageSource isrc, BlockingSingleQueue<FrameData> imageQueue)
        {
            this.url = url;
            this.isrc = isrc;
            this.imageQueue = imageQueue;
        }

        public void run()
        {
            try {
                this.setPriority(Thread.MAX_PRIORITY);
            } catch (IllegalArgumentException e) {
                System.err.println("Could not set thread priority. Priority out of range.");
            } catch (SecurityException e) {
                System.err.println("Could not set thread priority. Not permitted.");
            }

            isrc.start();
            while (true) {
                FrameData frmd = isrc.getFrame();
                if (frmd == null)
                    break;

                imageQueue.put(frmd);
            }

            System.out.printf("'%s' Out of frames!\n", url);
        }
    }

    class ProcessingThread extends Thread
    {
        public void run()
        {
            long lastutime = 0;

            Tic tic = new Tic();

            while (true)
            {
                if (captureOnce) {

                    tic.tic();
                    List<BufferedImage> imageSet = new ArrayList<BufferedImage>();
                    for (int i = 0; i < urls.length; i++) {
                        FrameData frmd = imageQueues[i].get();
                        BufferedImage im = ImageConvert.convertToImage(frmd);
                        imageSet.add(im);
                    }
                    System.out.printf("TIMING: %12.6f seconds to get image set\n", tic.toctic());

                    List<List<TagDetection>> detectionSet = new ArrayList<List<TagDetection>>();
                    for (int i = 0; i < urls.length; i++) {
                        BufferedImage im = imageSet.get(i);
                        List<TagDetection> detections = td.process(im, new double[] {im.getWidth()/2.0, im.getHeight()/2.0});
                        detectionSet.add(detections);
                    }
                    System.out.printf("TIMING: %12.6f seconds to detect tags\n", tic.toctic());

                    drawSet(imageSet, detectionSet);
                    System.out.printf("TIMING: %12.6f seconds to draw set\n", tic.toctic());

                    if (pg.gb("screenshots")) {
                        String path = String.format("/tmp/MultiCameraCalibrator-ScreenShot-%d-CameraPane%04d.png",
                                                    start_utime, imageCounter);
                        vcImages.writeScreenShot(new File(path), "png");
                    }

                    processSet(imageSet, detectionSet);
                    System.out.printf("TIMING: %12.6f seconds to process set\n", tic.toctic());

                    if (pg.gb("screenshots")) {
                        String path = String.format("/tmp/MultiCameraCalibrator-ScreenShot-%d-CalibratorPane%04d.png",
                                                    start_utime, imageCounter);
                        calibrator.getVisCanvas().writeScreenShot(new File(path), "png");
                        imageCounter++;
                    }

                    captureOnce = false;
                }
                else {

                    for (int i = 0; i < urls.length; i++) {
                        if (imageQueues[i].isEmpty())
                            continue;

                        FrameData frmd = imageQueues[i].get();
                        BufferedImage im = ImageConvert.convertToImage(frmd);

                        drawImage(i, im);
                    }
                }

                // sleep a little if we're spinning too fast
                long utime = TimeUtil.utime();
                long desired = 30000;
                if ((utime - lastutime) < desired) {
                    int sleepms = (int) ((desired - (utime-lastutime))*1e-3);
                    TimeUtil.sleep(sleepms);
                }
                lastutime = utime;
            }
        }
    }

    void drawSet(List<BufferedImage> imageSet, List<List<TagDetection>> detectionSet)
    {
        for (int i=0; i < imageSet.size(); i++) {
            drawImage(i, imageSet.get(i));
            drawDetections(i, detectionSet.get(i));
        }
    }

    double[] getXY0(int index)
    {
        double XY0[] = new double[2];

        assert(index < ifmts.length);
        for (int i=0; i <= index; i++)
            XY0[1] -= ifmts[i].height;

        return XY0;
    }

    double[] getXY1(int index)
    {
        double XY0[] = getXY0(index);
        double XY1[] = new double[2];

        XY1[0] = XY0[0] + ifmts[index].width;
        XY1[1] = XY0[1] + ifmts[index].height;

        return XY1;
    }

    double[][] ensurePixelTransform(int index)
    {
        double PixelsToVis[][] = PixelsToVisTransforms.get(index);

        if (PixelsToVis == null) {
            double XY0[] = getXY0(index);
            double XY1[] = getXY1(index);

            PixelsToVis = CameraMath.makeVisPlottingTransform(ifmts[index].width, ifmts[index].height,
                                                              XY0, XY1, true);
            PixelsToVisTransforms.put(index, PixelsToVis);
        }

        return PixelsToVis;
    }

    void drawImage(int index, BufferedImage im)
    {
        VisWorld.Buffer vb;
        double PixelsToVis[][] = ensurePixelTransform(index);

        vb = vwImages.getBuffer(String.format("Camera%d", index));
        vb.addBack(new VisLighting(false,
                                   new VisChain(PixelsToVis,
                                                new VzImage(new VisTexture(im, VisTexture.NO_MAG_FILTER |
                                                                               VisTexture.NO_MIN_FILTER |
                                                                               VisTexture.NO_REPEAT),
                                                            0))));
        vb.swap();
    }

    void drawDetections(int index, List<TagDetection> detections)
    {
        VisWorld.Buffer vb;
        double PixelsToVis[][] = ensurePixelTransform(index);

        vb = vwImages.getBuffer(String.format("Detections%d", index));
        VisChain chain = new VisChain();
        chain.add(PixelsToVis);
        for (TagDetection d : detections) {
            Color color = colorList.get(d.id);

            ArrayList<double[]> quad = new ArrayList<double[]>();
            quad.add(d.interpolate(-1,-1));
            quad.add(d.interpolate( 1,-1));
            quad.add(d.interpolate( 1, 1));
            quad.add(d.interpolate(-1, 1));

            chain.add(new VzMesh(new VisVertexData(quad),
                                 VzMesh.QUADS,
                                 new VzMesh.Style(color)));
        }
        vb.addBack(chain);
        vb.swap();
    }

    void processSet(List<BufferedImage> imageSet, List<List<TagDetection>> detectionSet)
    {
        calibrator.addOneImageSet(imageSet, detectionSet);

        List<RobustCameraCalibrator.GraphStats> stats =
            calibrator.iterateUntilConvergenceWithReinitalization(1.0, 0.01, 3, 50);

        calibrator.draw();
        calibrator.printCalibrationBlock();

        for (RobustCameraCalibrator.GraphStats s : stats) {
            if (s == null) {
                System.out.printf("Graph is null\n");
                continue;
            }
            System.out.printf("Graph with %d observations, MRE %12.6f pixels, MSE %12.6f pixels, SPD Error: %s\n",
                              s.numObs, s.MRE, s.MSE, s.SPDError ? "true" : "false");
        }
    }

    public static void main(String args[])
    {
        GetOpt opts  = new GetOpt();

        opts.addBoolean('h',"help",false,"See this help screen");
        opts.addString('u',"urls","","Camera URLs separated by semicolons");
        opts.addString('c',"class","april.camera.models.SimpleKannalaBrandtInitializer","Calibration model initializer class name");
        opts.addDouble('m',"spacing",0.0381,"Spacing between tags (meters)");

        if (!opts.parse(args)) {
            System.out.println("Option error: "+opts.getReason());
	    }

        String urllist = opts.getString("urls");
        String initclass = opts.getString("class");
        double spacing = opts.getDouble("spacing");

        if (opts.getBoolean("help") || urllist.isEmpty()){
            System.out.println("Usage:");
            opts.doHelp();
            System.exit(1);
        }

        String urls[] = urllist.split(";");

        List<CalibrationInitializer> initializers = new ArrayList<CalibrationInitializer>();

        for (int i=0; i < urls.length; i++) {
            Object obj = ReflectUtil.createObject(initclass);
            assert(obj != null);
            assert(obj instanceof CalibrationInitializer);
            CalibrationInitializer initializer = (CalibrationInitializer) obj;
            initializers.add(initializer);
        }

        new MultiCameraCalibrator(initializers, urls, spacing);
    }
}

