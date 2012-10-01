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
import april.camera.models.*;
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
    TagMosaic mosaic;
    TagDetector td;
    double PixelsToVisCam[][];
    double PixelsToVisSug[][];
    boolean once = true;

    Integer imwidth, imheight;

    Random r = new Random();//(1461234L);
    SyntheticTagMosaicImageGenerator simgen;
    // SyntheticTagMosaicImageGenerator.SyntheticImages suggestion;

    ArrayList<SuggestedImage> suggestDictionary = new ArrayList();

    SuggestedImage bestSuggestion;
    int suggestionNumber = 0;
    // double desiredXyzrpy[];

    List<ScoredImage> candidateImages;
    boolean waitingForBest = false;
    long startedWaiting = 0;
    double waitTime = 3.0; // seconds
    Integer minRow, minCol, maxRow, maxCol;
    int minRowUsed = -1, minColUsed = -1, maxRowUsed = -1, maxColUsed = -1;

    boolean captureNext = false;

    // save info for reinitialization
    double tagSpacingMeters;
    CalibrationInitializer initializer;

    List<List<BufferedImage>> imagesSet = new ArrayList<List<BufferedImage>>();
    List<List<List<TagDetection>>> detsSet = new ArrayList<List<List<TagDetection>>>();

    double bestScore, currentScore;
    BufferedImage bestInitImage;
    List<TagDetection> bestInitDetections;
    boolean bestInitUpdated = false;

    static class SuggestedImage
    {
        // Assumed to be in the actual image
        ArrayList<TagDetection> detections;

        // could be null
        BufferedImage im;
        double xyzrpy[];
    }

    // These only work with NotCentered
    static double[][] firstExtrinsics = {{ 0.004,   -0.084,   2*0.315,   -0.161,    0.795,    0.343}, // "good" #1
                                         {-0.070,   -0.044,    0.161,   -0.006,   -0.519,   -0.334}, // "good" #2
                                         {-0.050,   -0.058,    0.190,    0.863,    0.220,    0.142}}; // "good"


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
        this.mosaic = new TagMosaic(tf, tagSpacingMeters);
        this.td = new TagDetector(tf);
        this.tagSpacingMeters = tagSpacingMeters;
        this.initializer = initializer;

        // silence!
        CameraCalibrator.verbose = false;
        IntrinsicsEstimator.verbose = false;
        april.camera.models.SimpleKannalaBrandtInitializer.verbose = false;
        april.camera.models.SimpleCaltechInitializer.verbose = false;
        april.camera.models.KannalaBrandtInitializer.verbose = false;
        april.camera.models.DistortionFreeInitializer.verbose = false;
        april.camera.models.CaltechInitializer.verbose = false;

        ////////////////////////////////////////
        // GUI
        vwcal = new VisWorld();
        vlcal = new VisLayer("Calibrator", vwcal);
        vc = new VisCanvas(vlcal);

        pg = new ParameterGUI();
        pg.addButtons("skip","Skip this suggestion",
                      "manual","Manual capture",
                      "save","Save calibration and images");
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
        calibrator = new CameraCalibrator(Arrays.asList(initializer), tf, tagSpacingMeters, vlcal);

        ////////////////////////////////////////
        new AcquisitionThread().start();
        new ProcessingThread().start();
    }

    public void parameterChanged(ParameterGUI pg, String name)
    {
        // XXX Skip may not be possible when using a specific heuristic unless underlying state changes
        // if (name.equals("skip"))
        //     generateSuggestion(generateXyzrpy());

        if (name.equals("manual"))
            captureNext = true;

        if (name.equals("save") && calibrator != null)
            calibrator.saveCalibrationAndImages();
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

                if (imwidth == null || imheight == null) {
                    imwidth = im.getWidth();
                    imheight = im.getHeight();
                }
                assert(imwidth == im.getWidth() && imheight == im.getHeight());

                updateInitialization(im, detections);
                updateMosaic(detections);
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

        void updateInitialization(BufferedImage im, List<TagDetection> detections)
        {
            bestInitUpdated = false;

            if (bestInitImage != null && imagesSet.size() > 0)
                return;

            if (detections.size() < 4)
                return;

            InitializationVarianceScorer scorer = new InitializationVarianceScorer(calibrator,
                                                                                   imwidth, imheight);

            double score = scorer.scoreFrame(detections);
            EasyCal.this.currentScore = score;

            if (bestInitImage == null || score < bestScore) {
                bestScore = score;
                bestInitImage = im;
                bestInitDetections = detections;
                bestInitUpdated = true;

                System.out.printf("Reinitialized with score %12.6f\n", score);

                calibrator = new CameraCalibrator(Arrays.asList(initializer),
                                                  tf, tagSpacingMeters,
                                                  vlcal, false);
                calibrator.addImages(Arrays.asList(im), Arrays.asList(detections));
                calibrator.draw();
            }

        }

        void draw(BufferedImage im, List<TagDetection> detections)
        {
            updateLayers();

            ////////////////////////////////////////
            // camera image
            vb = vwside.getBuffer("Video");
            vb.setDrawOrder(100);
            vb.addBack(new VisLighting(false,
                                       new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_LEFT,
                                                        new VisChain(PixelsToVisCam,
                                                                     new VzImage(new VisTexture(im,
                                                                                                VisTexture.NO_MAG_FILTER |
                                                                                                VisTexture.NO_MIN_FILTER |
                                                                                                VisTexture.NO_REPEAT),
                                                                                 0)))));
            vb.swap();

            vb = vwside.getBuffer("Video HUD");
            vb.setDrawOrder(150);
            vb.addBack(new VisDepthTest(false,
                                        new VisPixCoords(VisPixCoords.ORIGIN.TOP,
                                                         new VzText(VzText.ANCHOR.TOP,
                                                                    "<<dropshadow=#FF000000,"+
                                                                    "monospaced-12-bold,white>>"+
                                                                    "Images are mirrored for display purposes"))));
            vb.swap();

            if (true)
            {
                FrameScorer fs = null;
                if (calibrator.getImages().size() >= 3) // XXX
                    fs = new PixErrScorer(calibrator, imwidth, imheight);

                double score = Double.POSITIVE_INFINITY;
                if (fs != null) score = fs.scoreFrame(detections);
                else            score = EasyCal.this.currentScore;

                vb = vwside.getBuffer("Score");
                vb.setDrawOrder(151);
                String str = null;

                if (score > 1000)
                    str = String.format("<<dropshadow=#FF000000,monospaced-12-bold,white>>Score: %e\n",score);
                else
                    str = String.format("<<dropshadow=#FF000000,monospaced-12-bold,white>>Score: %10.3f\n",score);

                vb.addBack(new VisDepthTest(false,
                                            new VisPixCoords(VisPixCoords.ORIGIN.TOP_RIGHT,
                                                             new VzText(VzText.ANCHOR.TOP_RIGHT, str))));
                // "Images are mirrored for display purposes"))));
                vb.swap();

            }
            ////////////////////////////////////////
            // suggested image
            vb = vwside.getBuffer("Suggestion");
            vb.setDrawOrder(0);
            if (bestSuggestion != null && bestSuggestion.im != null) {
                vb.addBack(new VisLighting(false,
                                           new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_LEFT,
                                                            new VisChain(PixelsToVisSug,
                                                                         new VzImage(new VisTexture(bestSuggestion.im,
                                                                                                    VisTexture.NO_MAG_FILTER |
                                                                                                    VisTexture.NO_MIN_FILTER |
                                                                                                    VisTexture.NO_REPEAT),
                                                                                     0)))));
            }
            vb.swap();

            ////////////////////////////////////////
            // detections
            vb = vwside.getBuffer("Detections");
            vb.setDrawOrder(110);
            VisChain chain = new VisChain();
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
            vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_LEFT, new VisChain(PixelsToVisCam, chain)));
            vb.swap();
        }

        void score(BufferedImage im, List<TagDetection> detections)
        {
            // if we haven't detected any tags yet...
            if (minRow == null || maxRow == null || minCol == null || maxCol == null) {
                vb = vwside.getBuffer("Suggestion HUD");
                vb.addBack(new VisDepthTest(false,
                                            new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_LEFT,
                                                             new VisChain(PixelsToVisSug,
                                                                          LinAlg.translate(im.getWidth()/2, im.getHeight()/2, 0),
                                                                          LinAlg.scale(-1, -1, 1),
                                                             new VzText(VzText.ANCHOR.CENTER,
                                                                        "<<dropshadow=#FF000000>>"+
                                                                        "<<monospaced-20-bold,green>>"+
                                                                        "Please hold target in front of camera")))));
                vb.swap();
                return;
            }

            // give a bit of user instruction
            vb = vwside.getBuffer("Suggestion HUD");
            vb.setDrawOrder(50);
            vb.addBack(new VisDepthTest(false,
                                        new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM,
                                                         new VzText(VzText.ANCHOR.BOTTOM,
                                                                    "<<dropshadow=#FF000000>>"+
                                                                    "<<monospaced-12-bold,white>>"+
                                                                    "Please align target with synthetic image"))));

            // clear buffers if we don't have any detections to plot
            if (detections.size() == 0) {
                vwcal.getBuffer("Suggestion").swap();
                vwside.getBuffer("Matches").swap();
                vwside.getBuffer("Suggestion Overlay").swap();
                vwside.getBuffer("Mosaic border").swap();

                return;
            }

            // fit a homography to the distorted tag detections
            List<double[]> xy_meters = new ArrayList<double[]>();
            List<double[]> xy_pixels = new ArrayList<double[]>();
            for (TagDetection d : detections) {
                xy_meters.add(LinAlg.select(mosaic.getPositionMeters(d.id), 0, 1));
                xy_pixels.add(LinAlg.select(d.cxy, 0, 1));
            }

            double K[][] = calibrator.getCalibrationIntrinsics(0);
            double H[][] = null;
            if (detections.size() > 4)
                H = CameraMath.estimateHomography(xy_meters, xy_pixels);

            // plot border
            ArrayList<double[]> tagBorderMosaic = new ArrayList<double[]>();
            tagBorderMosaic.add(mosaic.getPositionMeters(minCol-0.5, minRow-0.5));
            tagBorderMosaic.add(mosaic.getPositionMeters(maxCol+0.5, minRow-0.5));
            tagBorderMosaic.add(mosaic.getPositionMeters(maxCol+0.5, maxRow+0.5));
            tagBorderMosaic.add(mosaic.getPositionMeters(minCol-0.5, maxRow+0.5));

            // plot tag squares (for when you can't see the whole border)
            ArrayList<double[]> tagBlocksMosaic = new ArrayList<double[]>();
            for (int row=minRow; row <= maxRow; row++) {
                for (int col=minCol; col <= maxCol; col++) {
                    tagBlocksMosaic.add(mosaic.getPositionMeters(col-0.4, row-0.4));
                    tagBlocksMosaic.add(mosaic.getPositionMeters(col-0.4, row+0.4));
                    tagBlocksMosaic.add(mosaic.getPositionMeters(col+0.4, row+0.4));
                    tagBlocksMosaic.add(mosaic.getPositionMeters(col+0.4, row-0.4));
                }
            }

            if (H != null) {
                ArrayList<double[]> tagBorderImage = new ArrayList<double[]>();
                for (double p[] : tagBorderMosaic)
                    tagBorderImage.add(CameraMath.pixelTransform(H, new double[] { p[0], p[1] }));
                ArrayList<double[]> tagBlocksImage = new ArrayList<double[]>();
                for (double p[] : tagBlocksMosaic)
                    tagBlocksImage.add(CameraMath.pixelTransform(H, new double[] { p[0], p[1] }));

                vb = vwside.getBuffer("Mosaic border");
                vb.setDrawOrder(25);
                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_LEFT,
                                            new VisChain(PixelsToVisSug,
                                                         new VzLines(new VisVertexData(tagBorderImage),
                                                                     VzLines.LINES,
                                                                     new VzLines.Style(new Color(0, 255, 0), 2)),
                                                         new VzMesh(new VisVertexData(tagBlocksImage),
                                                                    VzMesh.QUADS,
                                                                    new VzMesh.Style(new Color(0, 255, 0, 200))),
                                                         new VzMesh(new VisVertexData(tagBorderImage),
                                                                    VzMesh.QUADS,
                                                                    new VzMesh.Style(new Color(0, 255, 0, 75))))));
                vb.swap();
            }

            // find matches between observed and suggested
            double totaldist = 0;
            int nmatches = 0;
            if (bestSuggestion != null) {
                SuggestedImage sim = bestSuggestion;

                for (TagDetection det1 : detections) {
                    for (TagDetection det2 : sim.detections) {
                        if (det1.id != det2.id)
                            continue;

                        totaldist += LinAlg.distance(det1.cxy, det2.cxy);
                        nmatches++;
                        break;
                    }
                }
            }
            double meandistthreshold = im.getWidth()/20.0;
            double meandist = totaldist/nmatches;

            if (H != null) {
                ArrayList<double[]> lines = new ArrayList<double[]>();
                double p1[] = findMatchingPoint(mosaic.getID(minCol, minRow));
                double p2[] = findMatchingPoint(mosaic.getID(maxCol, minRow));
                double p3[] = findMatchingPoint(mosaic.getID(maxCol, maxRow));
                double p4[] = findMatchingPoint(mosaic.getID(minCol, maxRow));
                if (p1 != null) {
                    lines.add(LinAlg.transform(PixelsToVisSug, CameraMath.pixelTransform(H,
                                                                    LinAlg.select(mosaic.getPositionMeters(minCol, minRow), 0, 1))));
                    lines.add(LinAlg.transform(PixelsToVisSug, p1));
                }
                if (p2 != null) {
                    lines.add(LinAlg.transform(PixelsToVisSug, CameraMath.pixelTransform(H,
                                                                    LinAlg.select(mosaic.getPositionMeters(maxCol, minRow), 0, 1))));
                    lines.add(LinAlg.transform(PixelsToVisSug, p2));
                }
                if (p3 != null) {
                    lines.add(LinAlg.transform(PixelsToVisSug, CameraMath.pixelTransform(H,
                                                                    LinAlg.select(mosaic.getPositionMeters(maxCol, maxRow), 0, 1))));
                    lines.add(LinAlg.transform(PixelsToVisSug, p3));
                }
                if (p4 != null) {
                    lines.add(LinAlg.transform(PixelsToVisSug, CameraMath.pixelTransform(H,
                                                                    LinAlg.select(mosaic.getPositionMeters(minCol, maxRow), 0, 1))));
                    lines.add(LinAlg.transform(PixelsToVisSug, p4));
                }

                vb = vwside.getBuffer("Matches");
                vb.setDrawOrder(20);
                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_LEFT,
                                            new VzLines(new VisVertexData(lines),
                                                        VzLines.LINES,
                                                        new VzLines.Style(Color.blue, 3))));
                vb.swap();
            }

            ////////////////////////////////////////
            // plot suggested and current mosaic positions in 3D

            // vb = vwcal.getBuffer("Suggestion");
            // if (K != null && H != null && suggestion != null) {

            //     double M2G_current[][] = CameraMath.decomposeHomography(H, K, xy_meters.get(0));
            //     double M2G_desired[][] = LinAlg.xyzrpyToMatrix(suggestion.MosaicToGlobal);

            //     double Tvis[][] = new double[][] { {  0,  0,  1,  0 },
            //                                        { -1,  0,  0,  0 } ,
            //                                        {  0, -1,  0,  0 } ,
            //                                        {  0,  0,  0,  1 } };

            //     VisVertexData vertices = new VisVertexData(tagBorderMosaic);
            //     vb.addBack(new VisChain(Tvis,
            //                             M2G_current,
            //                             new VzLines(vertices,
            //                                         VzLines.LINE_LOOP,
            //                                         new VzLines.Style(Color.green, 3)),
            //                             new VzMesh(vertices,
            //                                        VzMesh.QUADS,
            //                                        new VzMesh.Style(new Color(0, 255, 0, 100)))));
            //     vb.addBack(new VisChain(Tvis,
            //                             M2G_desired,
            //                             new VzLines(vertices,
            //                                         VzLines.LINE_LOOP,
            //                                         new VzLines.Style(Color.blue, 3)),
            //                             new VzMesh(vertices,
            //                                        VzMesh.QUADS,
            //                                        new VzMesh.Style(new Color(0, 0, 255, 100)))));
            // }
            // vb.swap();

            ////////////////////////////////////////
            // meter
            vb = vwside.getBuffer("Error meter");
            vb.setDrawOrder(30);
            double maxRectHeight = vc.getHeight()*0.3;
            double rectHeight = Math.min(maxRectHeight, maxRectHeight*meandist/meandistthreshold/3);
            double perc = rectHeight/maxRectHeight;
            double rectWidth = 30;
            Color barcolor = (perc < 0.33) ? Color.green : Color.red;
            VzRectangle tickmark = new VzRectangle(rectWidth, 0.01*maxRectHeight, new VzMesh.Style(Color.white));

            vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_LEFT,
                                        LinAlg.translate(rectWidth/2, vc.getHeight()/4-maxRectHeight/2, 0),
                                        new VisChain(LinAlg.translate(0, maxRectHeight/2, 0),
                                                     new VzRectangle(rectWidth, maxRectHeight, new VzMesh.Style(Color.black))),
                                        new VisChain(LinAlg.translate(0, rectHeight/2, 0),
                                                     new VzRectangle(rectWidth, rectHeight, new VzMesh.Style(barcolor))),
                                        new VisChain(LinAlg.translate(0, maxRectHeight/2, 0),
                                                     new VzRectangle(rectWidth, maxRectHeight, new VzLines.Style(Color.white, 2))),

                                        LinAlg.translate(-rectWidth/2, 0, 0),
                                        new VisDepthTest(false, new VzText(VzText.ANCHOR.LEFT,
                                                                           "<<dropshadow=#FF000000,"+
                                                                           "monospaced-12-bold,white>>"+
                                                                           "Perfect")),
                                        LinAlg.translate(0, maxRectHeight*1/3, 0),
                                        new VisDepthTest(false, new VzText(VzText.ANCHOR.LEFT,
                                                                          "<<dropshadow=#FF000000,"+
                                                                          "monospaced-12-bold,white>>"+
                                                                          "Good")),
                                        LinAlg.translate(0, maxRectHeight*2/3, 0),
                                        new VisDepthTest(false, new VzText(VzText.ANCHOR.LEFT,
                                                                          "<<dropshadow=#FF000000,"+
                                                                          "monospaced-12-bold,white>>"+
                                                                          "Bad"))));
            vb.swap();

            ////////////////////////////////////////
            ScoredImage si = new ScoredImage(im, detections, meandist);

            if (!waitingForBest && si.meandistance < meandistthreshold) {
                waitingForBest = true;
                startedWaiting = TimeUtil.utime();

                vb = vwside.getBuffer("Suggestion HUD");
                vb.addBack(new VisDepthTest(false,
                                            new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_LEFT,
                                                             new VisChain(PixelsToVisSug,
                                                                          LinAlg.translate(im.getWidth()/2, im.getHeight()/2, 0),
                                                                          LinAlg.scale(-1, -1, 1),
                                                             new VzText(VzText.ANCHOR.CENTER,
                                                                        "<<dropshadow=#FF000000>>"+
                                                                        "<<monospaced-20-bold,green>>"+
                                                                        "Almost there...")))));
                vb.swap();
            }

            // keep N best candidate images
            candidateImages.add(si);
            Collections.sort(candidateImages);
            List<ScoredImage> newCandidates = new ArrayList<ScoredImage>();
            for (int i=0; i < Math.min(10, candidateImages.size()); i++)
                newCandidates.add(candidateImages.get(i));
            candidateImages = newCandidates;

            if (captureNext || waitingForBest) {
                double waited = (TimeUtil.utime() - startedWaiting)*1.0e-6;

                if (captureNext || waited > waitTime) {

                    captureNext = false;

                    ////////////////////////////////////////
                    // "flash"
                    vwside.getBuffer("Suggestion").swap();
                    vwside.getBuffer("Suggestion Overlay").swap();
                    vwside.getBuffer("Matches").swap();
                    vwside.getBuffer("Suggestion HUD").swap();
                    vwside.getBuffer("Mosaic border").swap();

                    ////////////////////////////////////////
                    // use acquired image and suggest a new one
                    waitingForBest = false;
                    ScoredImage best = candidateImages.get(0);
                    candidateImages.clear();

                    addImage(best.im, best.detections);

                    // make a new suggestion
                    suggestionNumber++;
                    // XXX generateSuggestion(generateXyzrpy());
                    generateNextSuggestion();
                }
            }
            else {
                vwside.getBuffer("Suggestion HUD").swap();
            }
        }
    }

    private double[] findMatchingPoint(int id)
    {
        if (bestSuggestion == null)
            return null;

        for (TagDetection det : bestSuggestion.detections)
            if (det.id == id)
                return det.cxy;
        return null;
    }

    private void generateSuggestionsDict()
    {

        MultiGaussian mg = null;


        if (true) { // Use experimental data from expert datasets, works for NotCentered

            double mu[] = {0.162186, 0.021101, 0.222987, -0.019265, -0.000811, 3.14};

            double cov[][] = {{ 0.047790,  0.011413,  0.004247,  0.004409, -0.016715,         0 },
                              { 0.011413,  0.009917, -0.000494,  0.002122, -0.004196,         0 },
                              { 0.004247, -0.000494,  0.015034, -0.006155,  0.022634,         0 },
                              { 0.004409,  0.002122, -0.006155,  0.168764, -0.007430,         0 },
                              {-0.016715, -0.004196,  0.022634, -0.007430,  0.148120,         0 },
                              {        0,         0,         0,         0,         0,  0.04     }};
            mg = new MultiGaussian(cov,mu);
        } else { // Previous distribution, but only works for Centered
            double mu[] = {-.1,-.1,.2,.4,.4,.4};
            double cov[][]  = LinAlg.diag(new double[]{.2,.2,.4,.8,.8,.8});
            mg = new MultiGaussian(cov,mu);
        }

        ArrayList<SuggestedImage> sugs = new ArrayList();

        Random r2 = new Random(1034);

        ParameterizableCalibration cal = null;
        double params[] = null;
        if (calibrator != null)
            params = calibrator.getCalibrationParameters(0);
        if (params != null)
            cal = initializer.initializeWithParameters(imwidth, imheight, params);

        ArrayList<double[]> samples = mg.sampleMany(r2, 100);
        if (false) {
            samples.set(0,new double[] { 0.004,   -0.084,   2*0.315,   -0.161,    0.795,    0.343}); // "good" #1
            samples.set(1, new double[] {-0.070,   -0.044,    0.161,   -0.006,   -0.519,   -0.334}); // "good" #2
            samples.set(2, new double[] {-0.050,   -0.058,    0.190,    0.863,    0.220,    0.142}); // "good" #3
        }

        for (double extSample[] : samples) {
            SuggestedImage si = new SuggestedImage();
            si.detections = makeDetectionsFromExt(cal, extSample);
            if (si.detections.size() < 12) // XXX
                continue;
            si.xyzrpy = extSample;
            sugs.add(si);
        }
        System.out.printf("Made new dictionary with %d valid poses\n",sugs.size());
        suggestDictionary = sugs;
    }

    private ArrayList<TagDetection> makeDetectionsFromExt(Calibration cal, double mExtrinsics[])
    {
        ArrayList<TagDetection> detections = new ArrayList();
        for (int col = minColUsed; col <= maxColUsed; col++) {
            for (int row = minRowUsed; row <= maxRowUsed; row++) {
                TagDetection td = new TagDetection();
                td.id = row*mosaic.getMosaicWidth() + col;
                assert(td.id < tf.codes.length);
                //     continue;

                double world[] = mosaic.getPositionMeters(td.id);
                td.cxy = CameraMath.project(cal, LinAlg.xyzrpyToMatrix(mExtrinsics), world);


                if (td.cxy[0] < 0 || td.cxy[0] >= imwidth ||
                    td.cxy[1] < 0 || td.cxy[1] >= imheight)
                    continue;

                // XXX Also project the boundaries to make hxy???
                detections.add(td);
            }
        }
        return detections;
    }

    static SuggestedImage getBestSuggestion(List<SuggestedImage> suggestions, FrameScorer fs)
    {
        SuggestedImage bestImg = null;
        double lowestScore = Double.MAX_VALUE;

        for (SuggestedImage si : suggestions) {
            double score = fs.scoreFrame(si.detections);
            System.out.printf("%f ", score);
            if (score < lowestScore) {
                bestImg = si;
                lowestScore = score;
            }
        }
        System.out.println();

        assert(bestImg != null);
        return bestImg;
    }

    private void addImage(BufferedImage im, List<TagDetection> detections)
    {
        double origMRE = -1, newMRE = -1;
        boolean origSPD = true, newSPD = true;

        imagesSet.add(Arrays.asList(im));
        detsSet.add(Arrays.asList(detections));

        calibrator.addImages(imagesSet.get(imagesSet.size()-1), detsSet.get(detsSet.size()-1));

        try {
            calibrator.iterateUntilConvergence(0.01, 3, 50);
            origMRE = calibrator.getMRE();
        } catch (Exception ex) {
            origSPD = false;
        }

        if (origSPD == false || origMRE > 1.0)
        { // Try to reinitialize, check MRE, take the better one:
            CameraCalibrator cal = new CameraCalibrator(Arrays.asList(initializer), tf,
                                                        tagSpacingMeters, vlcal, false);
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

        // if both graphs failed, we'll reinitialize and *not* iterate.
        // this will get us something reasonable to render to the user
        if (!origSPD && !newSPD) {
            CameraCalibrator cal = new CameraCalibrator(Arrays.asList(initializer), tf,
                                                        tagSpacingMeters, vlcal, false);
            cal.addImageSet(imagesSet, detsSet, Collections.<double[]>nCopies(imagesSet.size(),null));
            calibrator = cal;
        }

        calibrator.draw();
    }

    private void updateLayers()
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
            vlside.backgroundColor = Color.white;
            vc.addLayer(vlside);
        }
        else {
            ((WindowedLayerManager) vlcal.layerManager).winwidth  = (int) (vc.getWidth()-w);
            ((WindowedLayerManager) vlcal.layerManager).winheight = (int) vc.getHeight();

            ((WindowedLayerManager) vlside.layerManager).winwidth  = (int) w;
            ((WindowedLayerManager) vlside.layerManager).winheight = (int) vc.getHeight();
        }
    }

    private void updateMosaic(List<TagDetection> detections)
    {
        if (detections == null || detections.size() == 0)
            return;

        // update the min/max column/row
        if (minRow == null || maxRow == null || minCol == null || maxCol == null) {
            TagDetection d = detections.get(0);
            minRow = mosaic.getRow(d.id);
            maxRow = minRow;
            minCol = mosaic.getColumn(d.id);
            maxCol = minCol;
        }

        for (TagDetection d : detections) {
            int row = mosaic.getRow(d.id);
            int col = mosaic.getColumn(d.id);

            minRow = Math.min(minRow, row);
            minCol = Math.min(minCol, col);
            maxRow = Math.max(maxRow, row);
            maxCol = Math.max(maxCol, col);
        }

        // we only need a new synthetic image if the min/max row/col changed
        // or if the initialization changed
        if (minRow == minRowUsed && maxRow == maxRowUsed &&
            minCol == minColUsed && maxCol == maxColUsed &&
            bestInitUpdated == false)
        {
            return;
        }

        minRowUsed = minRow;
        maxRowUsed = maxRow;
        minColUsed = minCol;
        maxColUsed = maxCol;

        int mosaicWidth  = mosaic.getMosaicWidth();
        int mosaicHeight = mosaic.getMosaicHeight();

        ArrayList<Integer> tagsToDisplay = new ArrayList<Integer>();
        for (int col = minCol; col <= maxCol; col++) {
            for (int row = minRow; row <= maxRow; row++) {

                int id = row*mosaicWidth + col;
                if (id > tf.codes.length)
                    continue;

                tagsToDisplay.add(id);
            }
        }

        simgen = new SyntheticTagMosaicImageGenerator(tf, imwidth, imheight, tagSpacingMeters, tagsToDisplay);
        candidateImages = new ArrayList<ScoredImage>();

        // XXX generateSuggestion(desiredXyzrpy);
        generateNextSuggestion();
    }

    private void generateNextSuggestion()
    {
        ParameterizableCalibration cal = null;
        double params[] = null;
        if (calibrator != null)
            params = calibrator.getCalibrationParameters(0);
        if (params != null)
            cal = initializer.initializeWithParameters(imwidth, imheight, params);

        if (cal == null)
            return;

        if (suggestionNumber < firstExtrinsics.length) {
            generateSuggestionsDict();

            FrameScorer fs = new InitializationVarianceScorer(calibrator, imwidth, imheight);
            bestSuggestion = getBestSuggestion(suggestDictionary, fs);
        }
        else {
            generateSuggestionsDict();

            FrameScorer fs = new PixErrScorer(calibrator, imwidth, imheight);
            bestSuggestion = getBestSuggestion(suggestDictionary, fs);
        }

        assert(bestSuggestion != null);
        bestSuggestion.im = simgen.generateImageNotCentered(cal, bestSuggestion.xyzrpy, false).distorted;
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
