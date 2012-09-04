package april.camera.calibrator;

import java.io.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.image.*;
import java.util.*;

import javax.swing.*;

import april.camera.*;
import april.jcam.*;
import april.tag.*;
import april.util.*;
import april.vis.*;

public class EasyCal implements ParameterListener
{
    // gui
    JFrame          jf;
    VisWorld        vwims;
    VisLayer        vlims;
    VisWorld        vwsug;
    VisLayer        vlsug;
    VisWorld        vwcal;
    VisLayer        vlcal;
    VisCanvas       vc;
    VisWorld.Buffer vb;
    ParameterGUI    pg;

    // camera
    String          url;
    ImageSource     isrc;
    BlockingSingleQueue<FrameData> imageQueue = new BlockingSingleQueue<FrameData>();

    CameraCalibrator calibrator;
    TagFamily tf;
    TagDetector td;
    double PixelsToVis[][];
    boolean once = true;

    public EasyCal(CalibrationInitializer initializer, String url, double tagSpacingMeters)
    {
        this.tf = new Tag36h11();
        this.td = new TagDetector(tf);

        ////////////////////////////////////////
        // GUI
        vwcal = new VisWorld();
        vlcal = new VisLayer("Calibrator", vwcal);
        vc = new VisCanvas(vlcal);

        pg = new ParameterGUI();
        pg.addButtons("save","Save images",
                      "print","Print calibration");
        pg.addListener(this);

        jf = new JFrame("EasyCal");
        jf.setLayout(new BorderLayout());

        JSplitPane pane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, vc, pg);
        pane.setDividerLocation(1.0);
        pane.setResizeWeight(1.0);

        jf.add(pane, BorderLayout.CENTER);
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        device.setFullScreenWindow(jf);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);

        ////////////////////////////////////////
        // Camera setup
        try {
            isrc = ImageSource.make(url);

        } catch (IOException ex) {
            System.err.println("Exception caught while making image source: " + ex);
            ex.printStackTrace();
            System.exit(-1);
        }

        ////////////////////////////////////////
        // Calibrator setup
        ArrayList<CalibrationInitializer> initializers = new ArrayList<CalibrationInitializer>();
        initializers.add(initializer);

        calibrator = new CameraCalibrator(initializers, tf, tagSpacingMeters, vlcal);

        ////////////////////////////////////////
        new AcquisitionThread().start();
        new ProcessingThread().start();
    }

    public void parameterChanged(ParameterGUI pg, String name)
    {
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

            System.out.println("Out of frames!");
        }
    }

    class ProcessingThread extends Thread
    {
        public void run()
        {
            long lastutime = 0;

            while (true) {

                FrameData frmd = imageQueue.get();

                BufferedImage im = ImageConvert.convertToImage(frmd);
                List<TagDetection> detections = td.process(im, new double[] {im.getWidth()/2.0, im.getHeight()/2.0});

                draw(im, detections);

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
            updateLayers(im.getWidth(), im.getHeight());

            vb = vwims.getBuffer("Video");
            vb.addBack(new VisLighting(false,
                                       new VisChain(PixelsToVis,
                                                    new VzImage(new VisTexture(im, VisTexture.NO_MAG_FILTER |
                                                                                   VisTexture.NO_MIN_FILTER |
                                                                                   VisTexture.NO_REPEAT),
                                                                0))));
            vb.swap();

            vb = vwims.getBuffer("Detections");
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
        }
    }

    private void updateLayers(int imwidth, int imheight)
    {
        double imaspect = ((double) imwidth) / imheight;
        double visaspect = ((double) vc.getWidth()) / vc.getHeight();
        double lh = 0.5;
        double lw = imaspect*lh/visaspect;

        double XY0[] = new double[2];
        double XY1[] = new double[] { imwidth, imheight };

        if (once) {
            once = false;
            PixelsToVis = CameraMath.makeVisPlottingTransform(imwidth, imheight,
                                                              XY0, XY1, true);

            vlcal.layerManager = new DefaultLayerManager(vlcal, new double[] { lw, 0, 1.0-lw, 1});
        }

        if (vwims == null || vlims == null || PixelsToVis == null) {

            vwims = new VisWorld();
            vlims = new VisLayer("Camera", vwims);
            vlims.layerManager = new DefaultLayerManager(vlims, new double[] {0, 0.5, lw, 0.5});
            ((DefaultCameraManager) vlims.cameraManager).interfaceMode = 1.5;
            vc.addLayer(vlims);

            vlims.cameraManager.fit2D(XY0, XY1, true);
        }

        if (vwsug == null || vlsug == null) {

            vwsug = new VisWorld();
            vlsug = new VisLayer("Suggestions", vwsug);

            vlsug.layerManager = new DefaultLayerManager(vlsug, new double[] {0, 0.0, lw, 0.5});
            ((DefaultCameraManager) vlsug.cameraManager).interfaceMode = 1.5;
            vc.addLayer(vlsug);
        }
    }

    public static void main(String args[])
    {
        GetOpt opts  = new GetOpt();

        opts.addBoolean('h',"help",false,"See this help screen");
        opts.addString('u',"url","","Camera URL");
        opts.addString('c',"class","april.camera.models.CaltechInitializer","Calibration model initializer class name");
        opts.addDouble('m',"spacing",0.0254,"Spacing between tags (meters)");

        if (!opts.parse(args)) {
            System.out.println("Option error: "+opts.getReason());
	    }

        String url = opts.getString("url");
        String initclass = opts.getString("class");
        double spacing = opts.getDouble("spacing");

        if (opts.getBoolean("help") || url.isEmpty()){
            System.out.println("Usage:");
            opts.doHelp();
            System.exit(1);
        }

        Object obj = ReflectUtil.createObject(initclass);
        assert(obj != null);
        assert(obj instanceof CalibrationInitializer);
        CalibrationInitializer initializer = (CalibrationInitializer) obj;

        new EasyCal(initializer, url, spacing);
    }
}
