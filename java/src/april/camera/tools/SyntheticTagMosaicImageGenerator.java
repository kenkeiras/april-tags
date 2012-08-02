package april.camera.tools;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;

import javax.imageio.*;
import javax.swing.*;

import april.camera.*;
import april.camera.models.*;
import april.jmat.*;
import april.tag.*;
import april.util.*;
import april.vis.*;

public class SyntheticTagMosaicImageGenerator
{
    VisWorld vw;
    VisLayer vl;
    VisCanvas vc;
    VisWorld.Buffer vb;

    TagFamily tf;
    TagDetector detector;

    int canvasWidth, canvasHeight;
    int imageWidth, imageHeight;

    BufferedImage example;
    BufferedImage mosaic;
    ArrayList<double[]> tagPositionsPixels; // pixel position *on the mosaic image*
    double tagSizeMeters;

    ArrayList<Integer> tagsToDisplay;

    double LocalToCamera[][];       // local frame to camera coordinates
    double MosaicPixelsToVis[][];   // mosaic pixel coordinate to vis coordinate transform

    double K[][];
    Calibration input;
    Calibration outputRectified;

    public class SyntheticImages
    {
        BufferedImage rectified;
        BufferedImage distorted;

        ArrayList<double[]> predictedTagCenters_rectified;
        ArrayList<double[]> predictedTagCenters_distorted;
    }

    public SyntheticTagMosaicImageGenerator(TagFamily tf, int _width, int _height, double tagSizeMeters)
    {
        this(tf, _width, _height, tagSizeMeters, null);
    }

    private ArrayList<Integer> getAllTagIds(TagFamily tf)
    {
        ArrayList<Integer> ids = new ArrayList<Integer>();
        for (int i=0; i < tf.codes.length; i++)
            ids.add(i);
        return ids;
    }

    public SyntheticTagMosaicImageGenerator(TagFamily tf, int _width, int _height, double tagSizeMeters,
                                            ArrayList<Integer> tagsToDisplay)
    {
        // parameters
        this.canvasWidth    = 2 * _width;
        this.canvasHeight   = 2 * _height;
        this.imageWidth     = _width;
        this.imageHeight    = _height;
        this.tf             = tf;
        this.detector       = new TagDetector(tf);
        this.tagSizeMeters  = tagSizeMeters;
        this.tagsToDisplay = tagsToDisplay;
        if (this.tagsToDisplay == null)
            this.tagsToDisplay = getAllTagIds(tf);

        // gui
        vw = new VisWorld();
        vl = new VisLayer(vw);
        // look down the X axis
        vl.cameraManager.uiLookAt(new double[] {  0.0,  0.0,  0.0 }, // eye
                                  new double[] {  1.0,  0.0,  0.0 }, // lookAt
                                  new double[] {  0.0,  0.0,  1.0 }, // up
                                  true);

        vc = new VisCanvas(vl);
        vc.setSize(canvasWidth, canvasHeight);

        // tag mosaic
        example = tf.makeImage(0);
        mosaic = tf.getAllImagesMosaic();

        int mosaicWidth     = (int) Math.sqrt(tf.codes.length);
        int mosaicHeight    = tf.codes.length / mosaicWidth + 1;
        {
            tagPositionsPixels = new ArrayList<double[]>();

            for (int y=0; y < mosaicHeight; y++) {
                for (int x=0; x < mosaicWidth; x++) {
                    int id = y*mosaicWidth + x;
                    if (id >= tf.codes.length)
                        continue;

                    tagPositionsPixels.add(new double[] { example.getWidth()  * (0.5 + x) ,
                                                          example.getHeight() * (0.5 + y) ,
                                                          0.0                             });
                }
            }
        }

        // transformation matrices
        LocalToCamera = new double[][] { {  0, -1,  0,  0} ,
                                         {  0,  0, -1,  0} ,
                                         {  1,  0,  0,  0} ,
                                         {  0,  0,  0,  1} };

        double scale = tagSizeMeters / example.getWidth();

        // get center of specified tag mosaic
        double xmin = tagPositionsPixels.get(tagsToDisplay.get(0))[0];
        double xmax = tagPositionsPixels.get(tagsToDisplay.get(0))[0];
        double ymin = tagPositionsPixels.get(tagsToDisplay.get(0))[1];
        double ymax = tagPositionsPixels.get(tagsToDisplay.get(0))[1];
        for (Integer id : tagsToDisplay) {
            xmin = Math.min(xmin, tagPositionsPixels.get(id)[0] - example.getWidth()/2);
            xmax = Math.max(xmax, tagPositionsPixels.get(id)[0] + example.getWidth()/2);
            ymin = Math.min(ymin, tagPositionsPixels.get(id)[1] - example.getHeight()/2);
            ymax = Math.max(ymax, tagPositionsPixels.get(id)[1] + example.getHeight()/2);
        }

        // put the mosaic on the YZ plane, centered about the X axis
        MosaicPixelsToVis = LinAlg.multiplyMany(LinAlg.rotateY(Math.PI/2),
                                                LinAlg.rotateZ(-Math.PI/2),
                                                LinAlg.scale(scale, scale, 1),
                                                LinAlg.translate(-(xmin+xmax)/2,
                                                                 -(ymin+ymax)/2,
                                                                 0));

        // camera settings
        double fov_deg = vl.cameraManager.getCameraTarget().perspective_fovy_degrees;
        double f = (canvasHeight / 2.0) / Math.tan(Math.PI/180.0 * fov_deg/2);

        K = new double[][] { { f, 0, canvasWidth /2 - 0.5 },
                             { 0, f, canvasHeight/2 - 0.5 },
                             { 0, 0, 1                    } };

        input = new DistortionFreeCalibration(new double[] {K[0][0], K[1][1]},
                                              new double[] {K[0][2], K[1][2]},
                                              canvasWidth, canvasHeight);

        outputRectified = new DistortionFreeCalibration(new double[] {K[0][0], K[1][1]},
                                                        new double[] {K[0][2]/2, K[1][2]/2},
                                                        imageWidth, imageHeight);
    }

    /** return the intrinsics for the rectified image.
      */
    public double[][] getIntrinsics()
    {
        return outputRectified.copyIntrinsics();
    }

    public SyntheticImages generateImage(Calibration outputDistorted, double xyzrpy[],
                                         boolean drawTagCenters)
    {
        double MosaicPixelsToGlobal[][] = LinAlg.matrixAB(LinAlg.xyzrpyToMatrix(xyzrpy),
                                                          MosaicPixelsToVis);

        ArrayList<double[]> tagPositionsGlobal = new ArrayList<double[]>();
        for (int id : tagsToDisplay) {
            double p[] = tagPositionsPixels.get(id);
            tagPositionsGlobal.add(LinAlg.transform(MosaicPixelsToGlobal, p));
        }

        // if we were showing the mosaic as a single image
        //VisTexture vt = new VisTexture(mosaic, VisTexture.NO_MIN_FILTER |
        //                                       VisTexture.NO_MAG_FILTER |
        //                                       VisTexture.NO_REPEAT |
        //                                       VisTexture.NO_ALPHA_MASK);
        //int flags = 0;
        //VzImage vzim = new VzImage(vt, flags);

        vb = vw.getBuffer("Image");
        //vb.addBack(new VisChain(MosaicPixelsToGlobal, vzim));
        for (int i=0; i < tagsToDisplay.size(); i++) {

            int id              = tagsToDisplay.get(i);
            double p[]          = tagPositionsPixels.get(id);
            BufferedImage tag   = tf.makeImage(id);

            VisTexture vt = new VisTexture(tag, VisTexture.NO_MIN_FILTER |
                                                VisTexture.NO_MAG_FILTER |
                                                VisTexture.NO_REPEAT |
                                                VisTexture.NO_ALPHA_MASK);
            int flags = 0;
            VzImage vzim = new VzImage(vt, flags);
            vb.addBack(new VisChain(MosaicPixelsToGlobal,
                                    LinAlg.translate(p[0] - 0.5 * example.getWidth()  ,
                                                     p[1] - 0.5 * example.getHeight() ,
                                                     0                                ),
                                    vzim));
        }
        vb.swap();

        vb = vw.getBuffer("Points");
        if (drawTagCenters)
            vb.addBack(new VisDepthTest(false,
                             new VzPoints(new VisVertexData(tagPositionsGlobal),
                                          new VzPoints.Style(Color.green, 10))));
        vb.swap();

        // render
        vc.drawSync();

        // get and save frame
        BufferedImage frame = new BufferedImage(canvasWidth, canvasHeight,
                                                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = (Graphics2D) frame.getGraphics();
        vc.paintComponent(g);

        SyntheticImages images = new SyntheticImages();

        {
            Rasterizer rasterizer = new BilinearRasterizer(input, outputRectified);
            images.rectified = rasterizer.rectifyImage(frame);

            images.predictedTagCenters_rectified = CameraMath.project(outputRectified,
                                                                      LocalToCamera,
                                                                      tagPositionsGlobal);
        }

        {
            Rasterizer rasterizer = new BilinearRasterizer(input, outputDistorted);
            images.distorted = rasterizer.rectifyImage(frame);

            images.predictedTagCenters_distorted = CameraMath.project(outputDistorted,
                                                                      LocalToCamera,
                                                                      tagPositionsGlobal);
        }

        return images;
    }

    private static class SIGenGUI implements ParameterListener
    {
        JFrame          jf;
        ParameterGUI    pg;
        JImage          jimr;
        JImage          jimd;

        SyntheticTagMosaicImageGenerator gen;
        int width, height;

        Random r = new Random(1461234L);

        public SIGenGUI(SyntheticTagMosaicImageGenerator gen, int width, int height)
        {
            this.gen = gen;
            this.width = width;
            this.height = height;

            pg = new ParameterGUI();
            pg.addCheckBoxes("showpoints","Show predicted tag centers (vis)",false,
                             "showPredictedTagCenters","Show predicted tag centers (image coordinates)", false);
            pg.addDoubleSlider("k1", "Distortion k1", -2, 2, -0.4);
            pg.addDoubleSlider("k2", "Distortion k2", -2, 2,  0.2);
            pg.addButtons("step","Sample new mosaic position");
            pg.addListener(this);

            jf = new JFrame("Synthetic image generator");
            jf.setLayout(new BorderLayout());

            jimr = new JImage();
            jimr.setFit(false);
            jimd = new JImage();
            jimd.setFit(false);

            JSplitPane imagePane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, jimr, jimd);
            imagePane.setDividerLocation(0.5);
            imagePane.setResizeWeight(0.5);

            JSplitPane pane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, imagePane, pg);
            pane.setDividerLocation(1.0);
            pane.setResizeWeight(1.0);

            jf.add(pane, BorderLayout.CENTER);
            jf.setSize(2*width + 100, height + 200);
            jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            jf.setVisible(true);

            // initial image
            generate(new double[] { 0.4, 0, 0, 0, 0, 0 });
        }

        public void parameterChanged(ParameterGUI pg, String name)
        {
            if (name.equals("step")) {
                double xyzrpy[] = new double[] { 0.1 + 0.5*r.nextDouble(),
                                                -0.1 + 0.2*r.nextDouble(),
                                                -0.1 + 0.2*r.nextDouble(),
                                                -0.4 + 0.8*r.nextDouble(),
                                                -0.4 + 0.8*r.nextDouble(),
                                                -0.4 + 0.8*r.nextDouble() };

                generate(xyzrpy);
            }
        }

        private void generate(double xyzrpy[])
        {
            double K[][] = gen.getIntrinsics();
            Calibration output = new SimpleCaltechCalibration(new double[] {K[0][0], K[1][1]},
                                                              new double[] {K[0][2], K[1][2]},
                                                              new double[] {pg.gd("k1"), pg.gd("k2")},
                                                              width, height);

            SyntheticTagMosaicImageGenerator.SyntheticImages images =
                        gen.generateImage(output, xyzrpy, pg.gb("showpoints"));

            if (pg.gb("showPredictedTagCenters")) {
                int buf[] = ((DataBufferInt) (images.rectified.getRaster().getDataBuffer())).getData();
                int w = images.rectified.getWidth();
                int h = images.rectified.getHeight();

                for (double p[] : images.predictedTagCenters_rectified) {
                    int x = (int) p[0];
                    int y = (int) p[1];

                    int s = 2;
                    for (int yy=y-s; yy<=y+s; yy++)
                        for (int xx=x-s; xx<=x+s; xx++)
                            if (xx >= 0 && xx < w && yy >= 0 && yy < h)
                                buf[yy*w+xx] = 0xFF0000FF;
                }
            }

            if (pg.gb("showPredictedTagCenters")) {
                int buf[] = ((DataBufferInt) (images.distorted.getRaster().getDataBuffer())).getData();
                int w = images.distorted.getWidth();
                int h = images.distorted.getHeight();

                for (double p[] : images.predictedTagCenters_distorted) {
                    int x = (int) p[0];
                    int y = (int) p[1];

                    int s = 2;
                    for (int yy=y-s; yy<=y+s; yy++)
                        for (int xx=x-s; xx<=x+s; xx++)
                            if (xx >= 0 && xx < w && yy >= 0 && yy < h)
                                buf[yy*w+xx] = 0xFF0000FF;
                }
            }

            jimr.setImage(images.rectified);
            jimd.setImage(images.distorted);
        }
    }

    public static void main(String args[])
    {
        TagFamily tf = new Tag36h11();
        int width = 752;
        int height = 480;

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

        SyntheticTagMosaicImageGenerator gen = new SyntheticTagMosaicImageGenerator(tf, width, height, pitch,
                                                                                    tagsToDisplay);

        SIGenGUI gui = new SIGenGUI(gen, width, height);
    }
}
