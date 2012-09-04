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
import april.camera.tools.*;
import april.jcam.*;
import april.jmat.*;
import april.tag.*;
import april.util.*;
import april.vis.*;

public class EasyCal implements ParameterListener
{
    // gui
    JFrame          jf;
    VisWorld        vwside;
    VisLayer        vlside;
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
    double PixelsToVisCam[][];
    double PixelsToVisSug[][];
    boolean once = true;

    Random r = new Random(1461234L);
    SyntheticTagMosaicImageGenerator simgen;
    SyntheticTagMosaicImageGenerator.SyntheticImages suggestion;
    List<ScoredImage> candidateImages;

    Integer minID, maxID;

    private class ScoredImage implements Comparable<ScoredImage>
    {
        public BufferedImage        im;
        public List<TagDetection>   detections;
        public double               meandistance;

        public ScoredImage(BufferedImage im, List<TagDetection> detections, double meandistance)
        {
            this.im           = im;
            this.detections   = detections;
            this.meandistance = meandistance;
        }

        public int compareTo(ScoredImage si)
        {
            return Double.compare(this.meandistance, si.meandistance);
        }
    }

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
        new IterateThread().start();
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
                score(im, detections);

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

            ////////////////////////////////////////
            // camera image
            vb = vwside.getBuffer("Video");
            vb.setDrawOrder(0);
            vb.addBack(new VisLighting(false,
                                       new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_LEFT,
                                                        new VisChain(PixelsToVisCam,
                                                                     new VzImage(new VisTexture(im,
                                                                                                VisTexture.NO_MAG_FILTER |
                                                                                                VisTexture.NO_MIN_FILTER |
                                                                                                VisTexture.NO_REPEAT),
                                                                                 0)))));
            vb.swap();

            ////////////////////////////////////////
            // suggested image
            vb = vwside.getBuffer("Suggestion");
            vb.setDrawOrder(0);
            vb.addBack(new VisLighting(false,
                                       new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_LEFT,
                                                        new VisChain(PixelsToVisSug,
                                                                     new VzImage(new VisTexture(EasyCal.this.suggestion.rectified,
                                                                                                VisTexture.NO_MAG_FILTER |
                                                                                                VisTexture.NO_MIN_FILTER |
                                                                                                VisTexture.NO_REPEAT),
                                                                                 0)))));
            vb.swap();

            ////////////////////////////////////////
            // detections
            vb = vwside.getBuffer("Detections");
            vb.setDrawOrder(10);
            VisChain camchain = new VisChain();
            VisChain sugchain = new VisChain();
            for (TagDetection d : detections) {
                double p0[] = d.interpolate(-1,-1);
                double p1[] = d.interpolate( 1,-1);
                double p2[] = d.interpolate( 1, 1);
                double p3[] = d.interpolate(-1, 1);

                sugchain.add(new VzMesh(new VisVertexData(p0, p1, p2, p3),
                                        VzMesh.QUADS,
                                        new VzMesh.Style(new Color(0, 255, 0))));

                camchain.add(new VzPoints(new VisVertexData(d.cxy),
                                          new VzPoints.Style(Color.blue, 3)));
                camchain.add(new VzLines(new VisVertexData(p0, p1, p2, p3, p0),
                                         VzLines.LINE_STRIP,
                                         new VzLines.Style(Color.blue, 2)));
                camchain.add(new VzLines(new VisVertexData(p0,p1),
                                         VzLines.LINE_STRIP,
                                         new VzLines.Style(Color.green, 2)));
                camchain.add(new VzLines(new VisVertexData(p0, p3),
                                         VzLines.LINE_STRIP,
                                         new VzLines.Style(Color.red, 2)));
            }
            vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_LEFT, new VisChain(PixelsToVisCam, camchain)));
            vb.swap();

            vb = vwside.getBuffer("Suggestion Overlay");
            vb.setDrawOrder(10);
            vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_LEFT, new VisChain(PixelsToVisSug, sugchain)));
            vb.swap();
        }

        void score(BufferedImage im, List<TagDetection> detections)
        {
            if (detections.size() == 0) {
                vwside.getBuffer("Matches").swap();
                return;
            }

            if (minID == null || maxID == null) {
                minID = detections.get(0).id;
                maxID = detections.get(0).id;
            }

            for (TagDetection d : detections) {
                minID = Math.min(minID, d.id);
                maxID = Math.max(maxID, d.id);
            }

            List<double[][]> lines = new ArrayList<double[][]>();
            double totaldist = 0;
            int nmatches = 0;
            for (int i=0; i < detections.size(); i++) {
                TagDetection d = detections.get(i);

                int matchid = d.id - minID;

                double p[] = null;
                for (int j=0; j < suggestion.tagids.length; j++) {
                    if (suggestion.tagids[j] == matchid) {
                        double pt[] = suggestion.predictedTagCenters_rectified.get(j);

                        if (pt[0] >= 0 && pt[0] < im.getWidth() &&
                            pt[1] >= 0 && pt[1] < im.getHeight())
                        {
                            p = pt;
                        }
                    }
                }

                if (p != null) {
                    totaldist += LinAlg.distance(d.cxy, p);
                    nmatches++;

                    double line[][] = new double[2][];
                    line[0] = LinAlg.transform(PixelsToVisSug, d.cxy);
                    line[1] = LinAlg.transform(PixelsToVisSug, p);
                    lines.add(line);
                }
            }

            // draw lines
            double matchlines[][] = new double[lines.size()*2][];
            for (int i=0; i < lines.size(); i++) {
                double line[][] = lines.get(i);
                matchlines[2*i+0] = line[0];
                matchlines[2*i+1] = line[1];
            }

            vb = vwside.getBuffer("Matches");
            vb.setDrawOrder(20);
            vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_LEFT,
                                        new VzLines(new VisVertexData(matchlines),
                                                    VzLines.LINES,
                                                    new VzLines.Style(Color.blue, 4))));
            vb.swap();

            ////////////////////////////////////////
            ScoredImage si = new ScoredImage(im, detections, totaldist/nmatches);

            if (si.meandistance < 30) { // XXX not a great criteria
                candidateImages.clear();

                // we've got a detection
                List<BufferedImage> allImages = new ArrayList<BufferedImage>();
                List<List<TagDetection>> allDetections = new ArrayList<List<TagDetection>>();
                allImages.add(si.im);
                allDetections.add(si.detections);

                calibrator.addImages(allImages, allDetections);

                // make a new suggestion
                generateSuggestion(generateXyzrpy());
            } else {
                // keep N best candidate images
                candidateImages.add(si);
                Collections.sort(candidateImages);
                List<ScoredImage> newCandidates = new ArrayList<ScoredImage>();
                for (int i=0; i < Math.min(10, candidateImages.size()); i++)
                    newCandidates.add(candidateImages.get(i));
                candidateImages = newCandidates;
            }

            // print
            for (int i=0; i < Math.min(10, candidateImages.size()); i++) {
                ScoredImage ci = candidateImages.get(i);
                System.out.printf("(%3d, %6.1f) ", ci.detections.size(), ci.meandistance);
            }
            System.out.println();
        }
    }

    class IterateThread extends Thread
    {
        public void run()
        {
            while (true) {
                if (calibrator != null) {
                    calibrator.iterate();
                    calibrator.draw();
                }

                TimeUtil.sleep(33);
            }
        }
    }

    private void updateLayers(int imwidth, int imheight)
    {
        double imaspect = ((double) imwidth) / imheight;
        double visaspect = ((double) vc.getWidth()) / vc.getHeight();

        double lh = 0.5;
        double lw = imaspect*lh/visaspect;

        double h = vc.getHeight() * 0.5;
        double w = h*imaspect;

        PixelsToVisCam = LinAlg.multiplyMany(CameraMath.makeVisPlottingTransform(imwidth, imheight,
                                                                                 new double[] {   0,   h },
                                                                                 new double[] {   w, 2*h },
                                                                                 true),
                                             LinAlg.translate(imwidth, 0, 0),
                                             LinAlg.scale(-1, 1, 1));

        PixelsToVisSug = LinAlg.multiplyMany(CameraMath.makeVisPlottingTransform(imwidth, imheight,
                                                                                 new double[] {   0,   0 },
                                                                                 new double[] {   w,   h },
                                                                                 true),
                                             LinAlg.translate(imwidth, 0, 0),
                                             LinAlg.scale(-1, 1, 1));

        if (once) {
            once = false;

            // cal layer
            vlcal.layerManager = new WindowedLayerManager(WindowedLayerManager.ALIGN.TOP_RIGHT, (int) (vc.getWidth()-w), (int) (vc.getHeight()));

            // image layer
            vwside = new VisWorld();
            vlside = new VisLayer("Side pane", vwside);
            vlside.layerManager = new WindowedLayerManager(WindowedLayerManager.ALIGN.BOTTOM_LEFT, (int) (w), (int) (vc.getHeight()));
            ((DefaultCameraManager) vlside.cameraManager).interfaceMode = 1.5;
            vlside.backgroundColor = new Color(50, 50, 50);
            vc.addLayer(vlside);

            // tags on a standard 1-page print of the tag mosaic
            double pitch = 0.0254;
            ArrayList<Integer> tagsToDisplay = new ArrayList<Integer>();
            for (int i=  0; i <=   9; i++) tagsToDisplay.add(i);
            for (int i= 24; i <=  33; i++) tagsToDisplay.add(i);
            for (int i= 48; i <=  57; i++) tagsToDisplay.add(i);
            for (int i= 72; i <=  81; i++) tagsToDisplay.add(i);
            for (int i= 96; i <= 105; i++) tagsToDisplay.add(i);
            for (int i=120; i <= 129; i++) tagsToDisplay.add(i);
            for (int i=144; i <= 153; i++) tagsToDisplay.add(i);

            simgen = new SyntheticTagMosaicImageGenerator(tf, imwidth, imheight, pitch, tagsToDisplay);

            generateSuggestion(null);

            candidateImages = new ArrayList<ScoredImage>();
        }
        else {
            ((WindowedLayerManager) vlcal.layerManager).winwidth  = (int) (vc.getWidth()-w);
            ((WindowedLayerManager) vlcal.layerManager).winheight = (int) vc.getHeight();

            ((WindowedLayerManager) vlside.layerManager).winwidth  = (int) w;
            ((WindowedLayerManager) vlside.layerManager).winheight = (int) vc.getHeight();
        }
    }

    private void generateSuggestion(double xyzrpy[])
    {
        if (xyzrpy == null)
            xyzrpy = new double[] {0.4, 0, 0, 0, 0.5, 0.5};

        this.suggestion = simgen.generateImage(null, xyzrpy, false);

    }

    private double[] generateXyzrpy()
    {
        double xyzrpy[] = new double[] { 0.1 + 0.5*r.nextDouble(),
                                        -0.1 + 0.2*r.nextDouble(),
                                        -0.1 + 0.2*r.nextDouble(),
                                        -0.4 + 0.8*r.nextDouble(),
                                        -0.4 + 0.8*r.nextDouble(),
                                        -0.4 + 0.8*r.nextDouble() };
        return xyzrpy;
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
