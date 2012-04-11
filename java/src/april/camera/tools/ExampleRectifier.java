package april.camera.tools;

import java.io.*;
import java.awt.image.*;
import javax.imageio.*;

import april.camera.*;
import april.config.*;
import april.jcam.*;
import april.util.*;

public class ExampleRectifier
{
    Calibration     cal;
    SyntheticView   view;
    Rasterizer      rasterizer;

    public ExampleRectifier(Config config, String imagepath) throws IOException
    {
        String toks[] = imagepath.split("\\.");
        String newpath = toks[0];
        for (int i=1; i+1 < toks.length; i++)
            newpath = String.format("%s.%s", newpath, toks[i]);
        newpath = String.format("%s.rectified.%s", newpath, toks[toks.length-1]);
        System.out.printf("Output image path: '%s'\n", newpath);

        System.out.println("Reading input image");
        BufferedImage in = ImageIO.read(new File(imagepath));
        in = ImageConvert.convertImage(in, BufferedImage.TYPE_INT_RGB);

        System.out.println("Creating camera calibration");
        cal = new CaltechCalibration(config);

        System.out.println("Creating rectified view");
        //view = new MaxInscribedRectifiedView(cal);
        view = new MaxRectifiedView(cal);

        System.out.println("Creating rasterizer");
        //rasterizer = new BilinearRasterizer(view);
        rasterizer = new NearestNeighborRasterizer(view);

        System.out.println("Rasterizing image");
        BufferedImage out = rasterizer.rectifyImage(in);

        System.out.println("Writing image to file");
        ImageIO.write(out, "png", new File(newpath));

        System.out.println("Done!");
    }

    public static void main(String args[])
    {
        GetOpt opts = new GetOpt();

        opts.addBoolean('h',"help",false,"See the help screen");
        opts.addString('c',"config","","Config file path");
        opts.addString('s',"childstring","","Child name");
        opts.addString('i',"image","","Image path");

        if (!opts.parse(args)) {
            System.out.println("Option error: " + opts.getReason());
        }

        String configpath = opts.getString("config");
        String childstring = opts.getString("childstring");
        String imagepath = opts.getString("image");

        if (opts.getBoolean("help") || configpath.isEmpty() || childstring.isEmpty() || imagepath.isEmpty()) {
            System.out.println("Usage:");
            opts.doHelp();
            System.exit(-1);
        }

        try {
            Config config = new ConfigFile(configpath);
            Config child = config.getChild(childstring);

            new ExampleRectifier(child, imagepath);

        } catch (IOException ex) {
            System.err.println("Exception: " + ex);
            ex.printStackTrace();
            System.exit(-1);
        }
    }
}
