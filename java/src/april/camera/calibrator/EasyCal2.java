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

import javax.imageio.*;

public class EasyCal2
{
    // gui
    JFrame          jf;
    VisWorld        vw;
    VisLayer        vl;
    VisCanvas       vc;

    // debug gui
    VisLayer vl2 = null;


    // camera
    String          url;
    ImageSource     isrc;
    BlockingSingleQueue<FrameData> imageQueue = new BlockingSingleQueue<FrameData>();

    CameraCalibrator calibrator;
    TagFamily tf;
    TagMosaic tm;
    TagDetector td;
    double PixelsToVis[][];
    boolean once = true;

    Integer imwidth, imheight;

    Random r = new Random();//(1461234L);
    SyntheticTagMosaicImageGenerator simgen;
    ArrayList<SuggestedImage> suggestDictionary = new ArrayList();
    SuggestedImage bestSuggestion;
    int suggestionNumber = 0;

    List<ScoredImage> candidateImages;
    boolean waitingForBest = false;
    long startedWaiting = 0;
    double waitTime = 3.5; // seconds
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

    List<Color> colorList;

    static class SuggestedImage
    {
        // Assumed to be in the actual image
        ArrayList<TagDetection> detections;

        // could be null
        BufferedImage im;
        double xyzrpy[];
        double xyzrpy_cen[];
    }

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

    public EasyCal2(CalibrationInitializer initializer, String url, double tagSpacingMeters, boolean debugGUI)
    {
        this.tf = new Tag36h11();
        this.tm = new TagMosaic(tf, tagSpacingMeters);
        this.td = new TagDetector(tf);
        this.tagSpacingMeters = tagSpacingMeters;
        this.initializer = initializer;

        // silence!
        CameraCalibrator.verbose = false;
        IntrinsicsEstimator.verbose = false;
        april.camera.models.SimpleKannalaBrandtInitializer.verbose = false;
        april.camera.models.KannalaBrandtInitializer.verbose = false;
        april.camera.models.DistortionFreeInitializer.verbose = false;
        april.camera.models.CaltechInitializer.verbose = false;
        april.camera.models.Radial4thOrderCaltechInitializer.verbose = false;
        april.camera.models.Radial6thOrderCaltechInitializer.verbose = false;

        ////////////////////////////////////////
        // GUI
        vw = new VisWorld();
        vl = new VisLayer("EasyCal2", vw);
        vc = new VisCanvas(vl);

        jf = new JFrame("EasyCal2");
        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);
        jf.setSize(1200, 600);
        //GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        //device.setFullScreenWindow(jf);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);

        // colors
        colorList = Palette.listAll();
        colorList.remove(0);
        Collections.shuffle(colorList, new Random(283819));



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

        if (debugGUI) {
            System.out.println("Making debug gui");
            VisWorld vw2 = new VisWorld();
            vl2 = new VisLayer(vw2);
            VisCanvas vc2 = new VisCanvas(vl2);

            JFrame jf2 = new JFrame("Debug");
            jf2.setLayout(new BorderLayout());
            jf2.add(vc2, BorderLayout.CENTER);
            jf2.setSize(752, 480);
            jf2.setLocation(1200,0);
            jf2.setVisible(true);
        }

        calibrator = new CameraCalibrator(Arrays.asList(initializer), tf, tagSpacingMeters, vl2, vl2 != null);



        ////////////////////////////////////////
        new AcquisitionThread().start();
        new ProcessingThread().start();
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
                //Tic tic = new Tic();
                List<TagDetection> detections = td.process(im, new double[] {im.getWidth()/2.0, im.getHeight()/2.0});
                //double dt = tic.toc();
                //System.out.printf("\rDetection time: %8.3f seconds", dt);

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

            if (imagesSet.size() != 0 || detections.size() < 8) // XXX
                return;

            InitializationVarianceScorer scorer = new InitializationVarianceScorer(calibrator,
                                                                                   imwidth, imheight);

            double score = scorer.scoreFrame(detections);
            EasyCal2.this.currentScore = score;

            if (bestInitImage == null || (score < 0.9*bestScore && bestScore > 20)) {
                bestScore = score;
                bestInitImage = im;
                bestInitDetections = detections;
                bestInitUpdated = true;

                System.out.printf("Reinitialized with score %12.6f\n", score);

                calibrator = new CameraCalibrator(Arrays.asList(initializer),
                                                  tf, tagSpacingMeters,
                                                  vl2, vl2 != null);

                calibrator.addImages(Arrays.asList(im), Arrays.asList(detections));
                calibrator.draw();
            }

        }

        void draw(BufferedImage im, List<TagDetection> detections)
        {
            VisWorld.Buffer vb;

            updateLayers();

            ////////////////////////////////////////
            // camera image
            vb = vw.getBuffer("Camera");
            vb.setDrawOrder(0);
            vb.addBack(new VisLighting(false,
                                       new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                                        new VisChain(PixelsToVis,
                                                                     new VzImage(new VisTexture(im,
                                                                                                VisTexture.NO_MAG_FILTER |
                                                                                                VisTexture.NO_MIN_FILTER |
                                                                                                VisTexture.NO_REPEAT),
                                                                                 0)))));
            vb.swap();

            vb = vw.getBuffer("HUD");
            vb.setDrawOrder(1000);
            vb.addBack(new VisDepthTest(false,
                                        new VisPixCoords(VisPixCoords.ORIGIN.TOP,
                                                         new VzText(VzText.ANCHOR.TOP,
                                                                    "<<dropshadow=#FF000000,"+
                                                                    "monospaced-12-bold,white>>"+
                                                                    "Images are mirrored for display purposes"))));
            vb.swap();

            vb = vw.getBuffer("Shade");
            vb.setDrawOrder(1);
            if (bestSuggestion != null) {
                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                            new VisChain(PixelsToVis,
                                                         LinAlg.translate(imwidth/2, imheight/2, 0),
                                                         new VzRectangle(imwidth, imheight,
                                                                         new VzMesh.Style(new Color(0, 0, 0, 128))))));
            }
            vb.swap();

            vb = vw.getBuffer("SuggestedTags");
            vb.setDrawOrder(20);
            if (bestSuggestion != null) {
                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                            new VisChain(PixelsToVis,
                                                         LinAlg.translate(imwidth/2, imheight/2, 0),
                                                         new VzRectangle(imwidth, imheight,
                                                                         new VzMesh.Style(new Color(0, 0, 0, 128))))));
                VisChain chain = new VisChain();
                for (TagDetection d : bestSuggestion.detections) {

                    Color color = colorList.get(d.id % colorList.size());
                    chain.add(new VzLines(new VisVertexData(d.p),
                                          VzLines.LINE_LOOP,
                                          new VzLines.Style(color, 2)));
                }
                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                            new VisChain(PixelsToVis, chain)));
            }
            vb.swap();

            vb = vw.getBuffer("Detections");
            vb.setDrawOrder(10);
            for (TagDetection d : detections) {
                Color color = colorList.get(d.id % colorList.size());

                ArrayList<double[]> quad = new ArrayList<double[]>();
                quad.add(d.interpolate(-1,-1));
                quad.add(d.interpolate( 1,-1));
                quad.add(d.interpolate( 1, 1));
                quad.add(d.interpolate(-1, 1));

                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                            new VisChain(PixelsToVis,
                                                         new VzMesh(new VisVertexData(quad),
                                                                    VzMesh.QUADS,
                                                                    new VzMesh.Style(color)))));
            }
            vb.swap();
        }

        void score(BufferedImage im, List<TagDetection> detections)
        {
            VisWorld.Buffer vb;

            // if we haven't detected any tags yet...
            if (minRow == null || maxRow == null || minCol == null || maxCol == null) {
                vb = vw.getBuffer("Suggestion HUD");
                vb.setDrawOrder(1000);
                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                            new VzText(VzText.ANCHOR.CENTER,
                                                       "<<dropshadow=#FF000000>>"+
                                                       "<<monospaced-20-bold,green>>"+
                                                       "Please hold target in front of camera")));
                vb.swap();
                return;
            }

            // give a bit of user instruction
            vb = vw.getBuffer("Suggestion HUD");
            vb.setDrawOrder(1000);
            vb.addBack(new VisDepthTest(false,
                                        new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM,
                                                         new VzText(VzText.ANCHOR.BOTTOM,
                                                                    "<<dropshadow=#FF000000>>"+
                                                                    "<<monospaced-12-bold,white>>"+
                                                                    "Please align target with synthetic image"))));

            // nothing more to do without detections
            if (detections.size() == 0)
                return;

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

            ////////////////////////////////////////
            // meter
            vb = vw.getBuffer("Error meter");
            vb.setDrawOrder(1000);
            double maxRectHeight = vc.getHeight()*0.3;
            double rectHeight = Math.min(maxRectHeight, maxRectHeight*meandist/meandistthreshold/3);
            double perc = rectHeight/maxRectHeight;
            double rectWidth = 25;
            Color barcolor = Color.white;

            vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_LEFT,
                                        LinAlg.translate(rectWidth/2, vc.getHeight()/2-maxRectHeight/2, 0),
                                        new VisChain(LinAlg.translate(0, maxRectHeight/2, 0),
                                                     new VzRectangle(rectWidth, maxRectHeight, new VzMesh.Style(Color.black))),
                                        new VisChain(LinAlg.translate(0, rectHeight/2, 0),
                                                     new VzRectangle(rectWidth, rectHeight, new VzMesh.Style(barcolor))),
                                        new VisChain(LinAlg.translate(0, maxRectHeight/2, 0),
                                                     new VzRectangle(rectWidth, maxRectHeight, new VzLines.Style(new Color(150, 150, 150), 2))),

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

                vb = vw.getBuffer("Suggestion HUD");
                vb.setDrawOrder(1000);
                vb.addBack(new VisDepthTest(false,
                                            new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                                             new VzText(VzText.ANCHOR.CENTER,
                                                                        "<<dropshadow=#FF000000>>"+
                                                                        "<<monospaced-20-bold,green>>"+
                                                                        "Almost there..."))));
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
                    vw.getBuffer("Suggestion HUD").swap();
                    new FlashThread().start();

                    ////////////////////////////////////////
                    // use acquired image and suggest a new one
                    waitingForBest = false;
                    ScoredImage best = candidateImages.get(0);
                    candidateImages.clear();

                    draw(best.im, best.detections);
                    addImage(best.im, best.detections);

                    // make a new suggestion
                    suggestionNumber++;
                    generateNextSuggestion();

                    updateRectifiedBorder();
                }
            }
            else {
                vw.getBuffer("Suggestion HUD").swap();
            }
        }
    }

    private void updateRectifiedBorder()
    {
        VisWorld.Buffer vb;

        vb = vw.getBuffer("Rectified outline");
        vb.setDrawOrder(1000);

        ParameterizableCalibration cal = null;
        double params[] = null;
        if (calibrator != null) params = calibrator.getCalibrationParameters(0);
        if (params != null)     cal    = initializer.initializeWithParameters(imwidth, imheight, params);

        if (cal != null) {
            ArrayList<double[]> border = MaxGrownInscribedRectifiedView.computeRectifiedBorder(cal);

            double minx = border.get(0)[0];
            double miny = border.get(0)[1];
            double maxx = minx, maxy = miny;
            for (double xy[] : border) {
                minx = Math.min(minx, xy[0]);
                maxx = Math.max(maxx, xy[0]);
                miny = Math.min(miny, xy[1]);
                maxy = Math.max(maxy, xy[1]);
            }

            double h = vc.getHeight()*0.25;
            double scale = h / (maxy - miny);

            vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.TOP_LEFT,
                                        new VisChain(LinAlg.scale(scale, -scale, 1),
                                                     LinAlg.translate(-minx, -miny, 0),
                                                     new VzLines(new VisVertexData(border),
                                                                 VzLines.LINE_LOOP,
                                                                 new VzLines.Style(Color.white, 2)))));
        }

        vb.swap();
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

    private double[] getObsMosaicCenter()
    {
        return LinAlg.scale(LinAlg.add(tm.getPositionMeters(minRow,minCol),
                                       tm.getPositionMeters(maxRow,maxCol)),
                            0.5);
    }

    private ArrayList<SuggestedImage> generateSuggestionsDict(Calibration cal)
    {
        int width = cal.getWidth();
        int height = cal.getHeight();

        // Compute single depth for the dictionary XXX
        double K[][] = cal.copyIntrinsics();
        double desiredDepths[] = {(tagSpacingMeters*K[0][0]) / (width/6) ,
                                  (tagSpacingMeters*K[0][0]) / (width/9) };

        // Place the center of the target:
        double centerXy[] = getObsMosaicCenter();

        DistortionFunctionVerifier verifier = new DistortionFunctionVerifier(cal);

        // Generate ~100 images, 3 angles each, 5 x 5 grid

        // These extrinsics are centered, so we can ensure the observed mosaic is mostly in view
        // ArrayList<double[]> centeredExt = new ArrayList();
        ArrayList<SuggestedImage> candidates = new ArrayList();

        int gridY = 4;
        int gridX = 4;
        int scaleY = height / gridY;
        int scaleX = width / gridY;

        for (double desiredDepth : desiredDepths) {
            for (int gy = 1; gy < gridY; gy++)
                for (int gx = 1; gx < gridX; gx++) {
                    double norm[] = cal.pixelsToNorm(new double[]{ gx*scaleX + r.nextGaussian()*2.0,
                                                                   gy*scaleY + r.nextGaussian()*2.0});

                    double xyz[] = {norm[0], norm[1], 1};
                    LinAlg.scaleEquals(xyz, desiredDepth + r.nextGaussian() * 0.01 );

                    // Choose which direction to rotate based on which image quadrant this is
                    int signRoll = MathUtil.sign(gx - gridX/2);
                    int signPitch = MathUtil.sign(gy - gridY/2);

                    double rpys[][] = {{ 0            + r.nextGaussian()*0.1, 0             + r.nextGaussian()*0.1, Math.PI/2 },
                                       { 0            + r.nextGaussian()*0.1, 0.8*signPitch + r.nextGaussian()*0.1, Math.PI/2 },
                                       { 0.8*signRoll + r.nextGaussian()*0.1, 0             + r.nextGaussian()*0.1, Math.PI/2 }};

                    for (double rpy[] : rpys) {
                        SuggestedImage si = new SuggestedImage();
                        si.xyzrpy_cen = concat(xyz,rpy);
                        si.xyzrpy = LinAlg.xyzrpyMultiply(si.xyzrpy_cen, LinAlg.xyzrpyInverse(LinAlg.resize(centerXy,6)));
                        candidates.add(si);
                    }
                }
        }

        ArrayList<SuggestedImage> passed = new ArrayList();
        for (SuggestedImage si : candidates) {
            // Pass in non-centered extrinsics
            si.detections = makeDetectionsFromExt(verifier, cal, si.xyzrpy);
            if (si.detections.size() < 12)
                continue;
            passed.add(si);
        }
        return passed;
    }

    // Candidate to move to LinAlg?
    public static double[] concat(double [] ... args)
    {
        int len = 0;
        for (double v[] : args)
            len += v.length;
        double out[] = new double[len];

        int off = 0;
        for (double v[] : args) {

            for (int i = 0; i < v.length; i++)
                out[off+ i] = v[i];
            off += v.length;
        }
        return out;
    }

    private ArrayList<TagDetection> makeDetectionsFromExt(DistortionFunctionVerifier verifier,
                                                          Calibration cal, double mExtrinsics[])
    {
        return SuggestUtil.makeDetectionsFromExt(cal, verifier, mExtrinsics, tm.getID(minCol, minRow),
                                                 tm.getID(maxCol, maxRow),
                                                 tm);
    }

    static SuggestedImage getBestSuggestion(List<SuggestedImage> suggestions, FrameScorer fs)
    {
        SuggestedImage bestImg = null;
        double lowestScore = Double.MAX_VALUE;

        for (SuggestedImage si : suggestions) {
            double score = fs.scoreFrame(si.detections);

            if (score < lowestScore) {
                bestImg = si;
                lowestScore = score;
            }
        }

        if (lowestScore == 1.0e-6) //JS: Why?
            return null;

        return bestImg;
    }

    private void addImage(BufferedImage im, List<TagDetection> detections)
    {
        // add the initialization frame before adding the specified frame
        if (imagesSet.size() == 0) {
            imagesSet.add(Arrays.asList(bestInitImage));
            detsSet.add(Arrays.asList(bestInitDetections));
        }

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
                                                        tagSpacingMeters, vl2, vl2 != null);
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
                                                        tagSpacingMeters, vl2, vl2 != null);
            cal.addImageSet(imagesSet, detsSet, Collections.<double[]>nCopies(imagesSet.size(),null));
            calibrator = cal;
        }

        calibrator.draw();
    }

    private void updateLayers()
    {
        double imaspect = ((double) imwidth) / imheight;
        double visaspect = ((double) vc.getWidth()) / vc.getHeight();

        double h = 0;
        double w = 0;

        if (imaspect > visaspect) {
            w = vc.getWidth();
            h = w / imaspect;
        } else {
            h = vc.getHeight();
            w = h*imaspect;
        }

        PixelsToVis = LinAlg.multiplyMany(LinAlg.translate(-w/2, -h/2, 0),
                                          CameraMath.makeVisPlottingTransform(imwidth, imheight,
                                                                              new double[] {   0,   0 },
                                                                              new double[] {   w,   h },
                                                                              true),
                                          LinAlg.translate(imwidth, 0, 0),
                                          LinAlg.scale(-1, 1, 1));
    }

    private void updateMosaic(List<TagDetection> detections)
    {
        if (detections == null || detections.size() == 0)
            return;

        // update the min/max column/row
        if (minRow == null || maxRow == null || minCol == null || maxCol == null) {
            TagDetection d = detections.get(0);
            minRow = tm.getRow(d.id);
            maxRow = minRow;
            minCol = tm.getColumn(d.id);
            maxCol = minCol;
        }

        for (TagDetection d : detections) {
            int row = tm.getRow(d.id);
            int col = tm.getColumn(d.id);

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

        int mosaicWidth  = tm.getMosaicWidth();
        int mosaicHeight = tm.getMosaicHeight();

        ArrayList<Integer> tagsToDisplay = new ArrayList<Integer>();
        for (int col = minCol; col <= maxCol; col++) {
            for (int row = minRow; row <= maxRow; row++) {

                int id = row*mosaicWidth + col;
                if (id > tf.codes.length)
                    continue;

                tagsToDisplay.add(id);
            }
        }

        //simgen = new SyntheticTagMosaicImageGenerator(tf, imwidth, imheight, tagSpacingMeters, tagsToDisplay);
        candidateImages = new ArrayList<ScoredImage>();

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

        suggestDictionary = generateSuggestionsDict(cal);
        System.out.printf("Made new dictionary with %d valid poses\n",suggestDictionary.size());

        // Write the dictionary to file
        if (false) {
            System.out.println("Writing suggestion dictionary to /tmp/dict. Standby...");
            try  {
                new File("/tmp/dict").mkdirs();
                int i = 0;
                for (SuggestedImage si : suggestDictionary) {
                    BufferedImage im = simgen.generateImageNotCentered(cal, si.xyzrpy, false).distorted;
                    ImageIO.write(im, "png", new File(String.format("/tmp/dict/IMG%03d.png", i++)));
                }
            } catch(IOException e) {}
        }

        SuggestedImage newSuggestion = null;

        if (suggestionNumber < 3) {
            FrameScorer fs = new InitializationVarianceScorer(calibrator, imwidth, imheight);
            newSuggestion = getBestSuggestion(suggestDictionary, fs);
        }
        else {
            FrameScorer fs = new PixErrScorer(calibrator, imwidth, imheight);
            newSuggestion = getBestSuggestion(suggestDictionary, fs);
        }

        if (newSuggestion != null)
            bestSuggestion = newSuggestion;
    }

    private class FlashThread extends Thread
    {
        public void run()
        {
            VisWorld.Buffer vb;

            vb = vw.getBuffer("Flash");
            vb.setDrawOrder(100);

            vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                        new VzRectangle(vc.getWidth(), vc.getHeight(),
                                                        new VzMesh.Style(Color.white))));
            vb.swap();

            for (int i = 0; i < 18; i++) {
                int alpha = (int) (Math.exp(-Math.pow(i*0.15 - 0.35, 2)) * 255);
                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                            new VzRectangle(vc.getWidth(), vc.getHeight(),
                                                            new VzMesh.Style(new Color(255, 255, 255, alpha)))));
                vb.swap();

                TimeUtil.sleep(15);
            }

            vb.swap();
        }
    }

    public static void main(String args[])
    {
        GetOpt opts  = new GetOpt();

        opts.addBoolean('h',"help",false,"See this help screen");
        opts.addString('u',"url","","Camera URL");
        opts.addString('c',"class","april.camera.models.CaltechInitializer","Calibration model initializer class name");
        opts.addDouble('m',"spacing",0.0254,"Spacing between tags (meters)");
        opts.addBoolean('\0',"debug-gui",false,"Display additional debugging information");

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

        new EasyCal2(initializer, url, spacing, opts.getBoolean("debug-gui"));
    }
}

