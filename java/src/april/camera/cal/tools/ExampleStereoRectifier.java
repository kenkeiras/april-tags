package april.camera.cal.tools;

import java.io.*;
import java.awt.image.*;
import java.util.*;

import javax.imageio.*;

import april.camera.*;
import april.camera.cal.*;
import april.config.*;
import april.jcam.*;
import april.jmat.*;
import april.util.*;

// XXX remove this
import java.awt.*;
import april.vis.*;
import javax.swing.*;

public class ExampleStereoRectifier
{
    CameraSet cameras;
    ArrayList<View> views;
    ArrayList<Rasterizer> rasterizers = new ArrayList<Rasterizer>();
    ArrayList<double[][]> extrinsics;
    View leftView;
    View rightView;

    public ExampleStereoRectifier(Config config, boolean inscribed,
                                  String leftImagePath, String rightImagePath) throws IOException
    {
        String leftOutputPath = newPathName(leftImagePath, inscribed);
        String rightOutputPath = newPathName(rightImagePath, inscribed);

        // load images
        BufferedImage leftImage = loadImage(leftImagePath);
        BufferedImage rightImage = loadImage(rightImagePath);

        // load calibrations
        cameras = new CameraSet(config);

        leftView  = cameras.getCalibration(0);
        rightView = cameras.getCalibration(1);

        //rightView = new ScaledView(0.5, rightView); // for testing
        //System.out.println(rightView.getCacheString());

        // make views
        createViewsExternal(inscribed);

        assert(cameras.size() == views.size());

        // make rasterizers
        {
            View input = leftView;
            View output = views.get(0);
            double C2G_input[][] = cameras.getExtrinsicsMatrix(0);
            double C2G_output[][] = extrinsics.get(0);

            rasterizers.add(new BilinearRasterizer(input, C2G_input,
                                                   output, C2G_output)); // XXX change cameraset to G2C?
        }
        {
            View input = rightView;
            View output = views.get(1);
            double C2G_input[][] = cameras.getExtrinsicsMatrix(1);
            double C2G_output[][] = extrinsics.get(1);

            rasterizers.add(new BilinearRasterizer(input, C2G_input,
                                                   output, C2G_output)); // XXX change cameraset to G2C?
        }
        assert(cameras.size() == rasterizers.size());

        // output images
        rasterizeImage( leftImage, rasterizers.get(0),  leftOutputPath);
        rasterizeImage(rightImage, rasterizers.get(1), rightOutputPath);
    }

    private String newPathName(String path, boolean inscribed)
    {
        String toks[] = path.split("\\.");
        String newpath = toks[0];
        for (int i=1; i+1 < toks.length; i++)
            newpath = String.format("%s.%s", newpath, toks[i]);
        if (inscribed)
            newpath = String.format("%s.stereo_rectified_inscribed.%s", newpath, toks[toks.length-1]);
        else
            newpath = String.format("%s.stereo_rectified.%s", newpath, toks[toks.length-1]);
        return newpath;
    }

    private BufferedImage loadImage(String path) throws IOException
    {
        BufferedImage im = ImageIO.read(new File(path));
        im = ImageConvert.convertImage(im, BufferedImage.TYPE_INT_RGB);

        return im;
    }

    private void createViewsExternal(boolean inscribed)
    {
        System.out.printf("Got %d cameras\n", cameras.size());
        assert(cameras.size() == 2);

        StereoRectification sr;

        if (inscribed)
            sr = StereoRectification.getMaxInscribedSR(leftView,
                                                       rightView,
                                                       cameras.getExtrinsicsMatrix(0),
                                                       cameras.getExtrinsicsMatrix(1));
        else
            sr = StereoRectification.getMaxSR(leftView,
                                              rightView,
                                              cameras.getExtrinsicsMatrix(0),
                                              cameras.getExtrinsicsMatrix(1));

        sr.showDebuggingGUI();

        views = sr.getViews();
        extrinsics = sr.getExtrinsics();
    }

    private void rasterizeImage(BufferedImage im, Rasterizer rasterizer, String outputPath) throws IOException
    {
        BufferedImage out = rasterizer.rectifyImage(im);

        // horizontal lines for debugging
        int raster[] = ((DataBufferInt) (out.getRaster().getDataBuffer())).getData();
        int width = out.getWidth();
        int height = out.getHeight();

        for (double yfrac = 0.05; yfrac < 1; yfrac += 0.05) {
            int y = (int) (height * yfrac);
            for (int x = 0; x < width; x++)
                raster[y*width + x] = (int) (0xFFFF0000);
        }

        ImageIO.write(out, "png", new File(outputPath));
    }

    public static void main(String args[])
    {
        GetOpt opts = new GetOpt();

        opts.addBoolean('h',"help",false,"See the help screen");
        opts.addString('c',"config","","Config file path");
        opts.addString('s',"childstring","","Child name");
        opts.addString('l',"leftimage","","Left image path");
        opts.addString('r',"rightimage","","Right image path");
        opts.addBoolean('i',"inscribed",false,"Use inscribed rectangle");

        if (!opts.parse(args)) {
            System.out.println("Option error: " + opts.getReason());
        }

        String configpath = opts.getString("config");
        String childstring = opts.getString("childstring");
        String leftimagepath = opts.getString("leftimage");
        String rightimagepath = opts.getString("rightimage");
        boolean inscribed = opts.getBoolean("inscribed");

        if (opts.getBoolean("help") || configpath.isEmpty() || childstring.isEmpty() ||
            leftimagepath.isEmpty() || rightimagepath.isEmpty())
        {
            System.out.println("Usage:");
            opts.doHelp();
            System.exit(-1);
        }

        try {
            Config config = new ConfigFile(configpath);
            Config child = config.getChild(childstring);

            new ExampleStereoRectifier(child, inscribed, leftimagepath, rightimagepath);

        } catch (IOException ex) {
            System.err.println("Exception: " + ex);
            ex.printStackTrace();
            System.exit(-1);
        }
    }
}
