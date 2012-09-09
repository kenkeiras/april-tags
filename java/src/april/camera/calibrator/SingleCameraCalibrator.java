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

public class SingleCameraCalibrator implements ParameterListener
{

    // GUI
    JFrame              jf;
    VisWorld            vw1;
    VisLayer            vl1;
    VisCanvas           vc1;
    VisWorld            vw2;
    VisLayer            vl2;
    VisCanvas           vc2;
    VisWorld.Buffer     vb;
    ParameterGUI        pg;

    // Camera
    String              url;
    ImageSource         isrc;
    BlockingSingleQueue<FrameData> imageQueue = new BlockingSingleQueue<FrameData>();

    // Wait to read the next image until the previous one is done being processed?
    boolean blockOnProcessing = false; // Only set to true for img-dir processing

    // Calibrator
    CameraCalibrator    calibrator;
    boolean             capture = false;
    boolean             captureOnce = false;

    TagFamily tf;
    TagDetector td;

    double PixelsToVis[][];

    // Save the info we need to replace calibrator later on, if necessary
    double tagSpacing_m;
    CalibrationInitializer initializer;

    // Keep track of all the images and detections we've done, we'll use them to reinitialize as necessary
    List<List<BufferedImage>> imagesSet = new ArrayList();
    List<List<List<TagDetection>>>  detsSet = new ArrayList();

    public SingleCameraCalibrator(CalibrationInitializer initializer, String url, double tagSpacing_m,
                                  boolean autocapture, boolean block)
    {
        this.capture = autocapture;
        this.blockOnProcessing = block;

        this.tf = new Tag36h11();
        this.td = new TagDetector(tf);
        this.initializer = initializer;
        this.tagSpacing_m = tagSpacing_m;

        // silence!
        CameraCalibrator.verbose = false;
        IntrinsicsEstimator.verbose = false;
        april.camera.models.SimpleKannalaBrandtInitializer.verbose = false;
        april.camera.models.SimpleCaltechInitializer.verbose = false;
        april.camera.models.KannalaBrandtInitializer.verbose = false;
        april.camera.models.DistortionFreeInitializer.verbose = false;
        april.camera.models.CaltechInitializer.verbose = false;

        ////////////////////////////////////////////////////////////////////////////////
        // GUI setup
        vw1 = new VisWorld();
        vl1 = new VisLayer(vw1);
        vc1 = new VisCanvas(vl1);
        vw2 = new VisWorld();
        vl2 = new VisLayer(vw2);
        vc2 = new VisCanvas(vl2);

        pg = new ParameterGUI();
        pg.addCheckBoxes("enablecamera","Enable camera",true,
                         "autocapture","Autocapture",autocapture);
        pg.addButtons("captureOnce","Capture once",
                      "savedetections","Save detections",
                      "savereprojections","Save reprojection error histogram",
                      "save","Save calibration and images");
        pg.addListener(this);

        jf = new JFrame("Single camera calibrator");
        jf.setLayout(new BorderLayout());

        JSplitPane canvases = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, vc1, vc2);
        canvases.setDividerLocation(0.5);
        canvases.setResizeWeight(0.5);

        JSplitPane jspane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, canvases, pg);
        jspane.setDividerLocation(1.0);
        jspane.setResizeWeight(1.0);

        jf.add(jspane, BorderLayout.CENTER);
        jf.setSize(1200, 600);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);

        ////////////////////////////////////////////////////////////////////////////////
        // Camera setup
        try {
            isrc = ImageSource.make(url);

        } catch (IOException ex) {
            System.err.println("Exception caught while making image source: " + ex);
            ex.printStackTrace();
            System.exit(-1);
        }

        // Calibrator setup
        calibrator = new CameraCalibrator(Arrays.asList(initializer), tf,
                                          tagSpacing_m, vl2);

        // Threads
        new ProcessingThread().start();
        new AcquisitionThread().start();
    }

    public void parameterChanged(ParameterGUI pg, String name)
    {
        if (name.equals("captureOnce")) {
            if (captureOnce) captureOnce = false;
            else             captureOnce = true;
        }

        if (name.equals("save") && calibrator != null)
            calibrator.saveCalibrationAndImages();

        if (name.equals("savedetections") && calibrator != null)
            calibrator.saveDetections();

        if (name.equals("savereprojections") && calibrator != null)
            calibrator.saveReprojectionErrors();
    }

    class AcquisitionThread extends Thread
    {
        public AcquisitionThread()
        {
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

                if (blockOnProcessing)
                    imageQueue.waitTillEmpty();

                imageQueue.put(frmd);
            }
            System.out.println("Out of frames!");
        }
    }

    class ProcessingThread extends Thread
    {
        public void run()
        {
            long lastutime = 0;

            while (true) {

                if (pg.gb("enablecamera")) {
                    FrameData frmd = imageQueue.get();

                    BufferedImage im = ImageConvert.convertToImage(frmd);
                    List<TagDetection> detections = td.process(im, new double[] {im.getWidth()/2.0, im.getHeight()/2.0});

                    draw(im, detections);

                    if (pg.gb("autocapture") || captureOnce) {
                        if (captureOnce) captureOnce = false;
                        process(im, detections);
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

        void draw(BufferedImage im, List<TagDetection> detections)
        {
            if (PixelsToVis == null) {
                double XY0[] = new double[2];
                double XY1[] = new double[] { im.getWidth(), im.getHeight() };
                PixelsToVis = CameraMath.makeVisPlottingTransform(im.getWidth(), im.getHeight(),
                                                                  XY0, XY1, true);
                vl1.cameraManager.fit2D(XY0, XY1, true);
            }

            vb = vw1.getBuffer("Video");
            vb.addBack(new VisLighting(false,
                                       new VisChain(PixelsToVis,
                                                    new VzImage(new VisTexture(im, VisTexture.NO_MAG_FILTER |
                                                                                   VisTexture.NO_MIN_FILTER |
                                                                                   VisTexture.NO_REPEAT),
                                                                0))));
            vb.swap();

            vb = vw1.getBuffer("Detections");
            VisChain chain = new VisChain();
            chain.add(PixelsToVis);
            for (TagDetection d : detections) {
                double p0[] = d.interpolate(-1,-1);
                double p1[] = d.interpolate( 1,-1);
                double p2[] = d.interpolate( 1, 1);
                double p3[] = d.interpolate(-1, 1);

                chain.add(new VzPoints(new VisVertexData(d.cxy),
                                       new VzPoints.Style(Color.blue, 3)));
                chain.add(new VzLines(new VisVertexData(p0, p1, p2, p3, p0),
                                      VzLines.LINE_STRIP,
                                      new VzLines.Style(Color.blue, 2)));
                chain.add(new VzLines(new VisVertexData(p0,p1),
                                      VzLines.LINE_STRIP,
                                      new VzLines.Style(Color.green, 2)));
                chain.add(new VzLines(new VisVertexData(p0, p3),
                                      VzLines.LINE_STRIP,
                                      new VzLines.Style(Color.red, 2)));
            }
            vb.addBack(chain);
            vb.swap();

            vb = vw1.getBuffer("HUD");
            if (captureOnce)
                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.TOP_LEFT,
                                            new VzText(VzText.ANCHOR.TOP_LEFT,
                                                       "<<monospaced-12,green>>Waiting to capture")));
            else
                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.TOP_LEFT,
                                            new VzText(VzText.ANCHOR.TOP_LEFT,
                                                       String.format("<<monospaced-12,%s>>Autocapture %s",
                                                                     pg.gb("autocapture") ? "green" : "red",
                                                                     pg.gb("autocapture") ? "enabled" : "disabled"))));
            vb.swap();
        }

        void process(BufferedImage im, List<TagDetection> detections)
        {
            double origMRE = -1, newMRE = -1;
            boolean origSPD = true, newSPD = true;

            imagesSet.add(Arrays.asList(im));
            detsSet.add(Arrays.asList(detections));

            calibrator.addImages(imagesSet.get(imagesSet.size()-1), detsSet.get(detsSet.size()-1));

            try {
                calibrator.iterateUntilConvergence(0.01, 1, 50);
                origMRE = calibrator.getMRE();
            } catch (Exception ex) {
                origSPD = false;
            }

            if (origSPD == false || origMRE > 1.0)
            { // Try to reinitialize, check MRE, take the better one:
                CameraCalibrator cal = new CameraCalibrator(Arrays.asList(initializer), tf,
                                                            tagSpacing_m, vl2, false);
                cal.addImageSet(imagesSet, detsSet, Collections.<double[]>nCopies(imagesSet.size(),null));

                try {
                    cal.iterateUntilConvergence(0.01, 3, 50);
                    newMRE = cal.getMRE();
                } catch (Exception ex) {
                    newSPD = false;
                }

                if (origSPD && newSPD) {
                    double MREimprovement = origMRE - newMRE;
                    if (MREimprovement > 0.001)
                        calibrator = cal;

                    System.out.printf("[%3d] [Using %s] Original MRE: %12.6f Re-init MRE: %12.6f\n",
                                      imagesSet.size(), (calibrator == cal) ? "new " : "orig", origMRE, newMRE);

                } else if (origSPD && !newSPD) {
                    // keep current calibrator

                    System.out.printf("[%3d] [Using orig] Original MRE: %12.6f Re-init %17s\n",
                                      imagesSet.size(), origMRE, "not SPD");

                } else if (!origSPD && newSPD) {
                    // use new calibrator
                    calibrator = cal;

                    System.out.printf("[%3d] [Using new ] Original %17s Re-init MRE: %12.6f\n",
                                      imagesSet.size(), "not SPD", newMRE);

                } else {
                    // oh boy.

                    System.out.printf("[%3d] [Using orig] Original %17s Re-init %17s\n",
                                      imagesSet.size(), "not SPD", "not SPD");

                }
            }
            else {
                System.out.printf("[%3d] [Using orig] Original MRE: %12.6f\n",
                                  imagesSet.size(), origMRE);
            }

            calibrator.draw();
        }
    }

    public static void main(String args[])
    {
        GetOpt opts  = new GetOpt();

        opts.addBoolean('h',"help",false,"See this help screen");
        opts.addString('u',"url","","Camera URL");
        opts.addString('c',"class","april.camera.models.CaltechInitializer","Calibration model initializer class name");
        opts.addDouble('m',"spacing",0.0254,"Spacing between tags (meters)");
        opts.addBoolean('a',"autocapture",false,"Automatically capture frames");

        if (!opts.parse(args)) {
            System.out.println("Option error: "+opts.getReason());
	    }

        String url = opts.getString("url");
        String initclass = opts.getString("class");
        double spacing = opts.getDouble("spacing");
        boolean autocapture = opts.getBoolean("autocapture");

        if (opts.getBoolean("help") || url.isEmpty()){
            System.out.println("Usage:");
            opts.doHelp();
            System.exit(1);
        }

        Object obj = ReflectUtil.createObject(initclass);
        assert(obj != null);
        assert(obj instanceof CalibrationInitializer);
        CalibrationInitializer initializer = (CalibrationInitializer) obj;

        new SingleCameraCalibrator(initializer, url, spacing, autocapture, url.startsWith("dir://"));
    }
}
