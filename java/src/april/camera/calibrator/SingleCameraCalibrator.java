package april.camera.calibrator;

import java.io.*;
import java.awt.*;
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

    // Calibrator
    CameraCalibrator    calibrator;
    boolean             capture = false;
    boolean             captureOnce = false;

    boolean autoiterate = false;

    public SingleCameraCalibrator(CalibrationInitializer initializer, String url, double tagSpacing_m,
                                  boolean autocapture)
    {
        this.capture = autocapture;

        ////////////////////////////////////////////////////////////////////////////////
        // GUI setup
        vw1 = new VisWorld();
        vl1 = new VisLayer(vw1);
        vc1 = new VisCanvas(vl1);
        vw2 = new VisWorld();
        vl2 = new VisLayer(vw2);
        vc2 = new VisCanvas(vl2);

        pg = new ParameterGUI();
        pg.addButtons("captureOnce","Capture once","capture","Toggle image capturing",
                      "save","Save images",
                      "iterateonce","Iterate once","iterate","Toggle auto iteration",
                      "print","Print calibration");
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
        ArrayList<CalibrationInitializer> initializers = new ArrayList<CalibrationInitializer>();
        initializers.add(initializer);

        calibrator = new CameraCalibrator(initializers, new Tag36h11(),
                                          tagSpacing_m, vl2);

        // Threads
        new ProcessingThread().start();
        new AcquisitionThread().start();
        new GraphUpdateThread().start();
    }

    public void parameterChanged(ParameterGUI pg, String name)
    {
        if (name.equals("capture")) {
            vb = vw1.getBuffer("HUD");

            if (capture) {
                capture = false;
                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.TOP_LEFT,
                                            new VzText(VzText.ANCHOR.TOP_LEFT,
                                                       "<<monospaced-12,red>>Capturing disabled")));
            } else {
                capture = true;
                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.TOP_LEFT,
                                            new VzText(VzText.ANCHOR.TOP_LEFT,
                                                       "<<monospaced-12,green>>Capturing enabled")));
            }
            vb.swap();
        }

        if (name.equals("captureOnce")) {
            vb = vw1.getBuffer("HUD");

            if (captureOnce) {
                captureOnce = false;
                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.TOP_LEFT,
                                            new VzText(VzText.ANCHOR.TOP_LEFT,
                                                       "<<monospaced-12,red>>Capturing disabled")));
            } else {
                captureOnce = true;
                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.TOP_LEFT,
                                            new VzText(VzText.ANCHOR.TOP_LEFT,
                                                       "<<monospaced-12,green>>Waiting to capture")));
            }
            vb.swap();
        }

        if (name.equals("iterateonce") && calibrator != null)
            calibrator.iterate();

        if (name.equals("iterate"))
            autoiterate = autoiterate ? false : true;

        if (name.equals("print") && calibrator != null)
            calibrator.printCalibrationBlock();

        if (name.equals("save") && calibrator != null)
            calibrator.saveImages();
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

                imageQueue.put(frmd);
            }
        }
    }

    class ProcessingThread extends Thread
    {
        boolean once = true;

        public void run()
        {
            while (true) {
                FrameData frmd = imageQueue.get();

                BufferedImage im = ImageConvert.convertToImage(frmd);

                draw(im);

                if (capture || captureOnce) {
                    if (captureOnce) {
                        captureOnce = false;
                        vb = vw1.getBuffer("HUD");
                        vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.TOP_LEFT,
                                                    new VzText(VzText.ANCHOR.TOP_LEFT,
                                                               "<<monospaced-12,red>>Capturing disabled")));
                        vb.swap();
                    }

                    process(im);
                }
                TimeUtil.sleep(100);
            }
        }

        void draw(BufferedImage im)
        {
            vb = vw1.getBuffer("Video");

            vb.addBack(new VisLighting(false,
                                       new VzImage(new VisTexture(im,
                                                                  VisTexture.NO_MAG_FILTER | VisTexture.NO_MIN_FILTER | VisTexture.NO_REPEAT),
                                                   VzImage.FLIP)));
            vb.swap();

            if (once) {
                once = false;
                vl1.cameraManager.fit2D(new double[2], new double[] {im.getWidth(), im.getHeight()}, true);
            }
        }

        void process(BufferedImage im)
        {
            System.out.println("Process");
            ArrayList<BufferedImage> images = new ArrayList<BufferedImage>();
            images.add(im);

            calibrator.addImages(images);
        }
    }

    class GraphUpdateThread extends Thread
    {
        long last = 0;

        public void run()
        {
            while (true) {
                if (calibrator == null) {
                    TimeUtil.sleep(33);
                    continue;
                }

                if (autoiterate)
                    calibrator.iterate();

                long now = TimeUtil.utime();
                double dt = (now - last) * 1.0E-6;
                if (dt > 0.050) {
                    calibrator.draw();
                    last = now;
                }
            }
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

        new SingleCameraCalibrator(initializer, url, spacing, autocapture);
    }
}
