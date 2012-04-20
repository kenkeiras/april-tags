package april.camera.cal.tools;

import java.io.*;
import java.awt.image.*;
import javax.imageio.*;

import april.camera.cal.*;
import april.config.*;
import april.jcam.*;
import april.jmat.*;
import april.util.*;

public class ExampleRectifier
{
    View input;
    View output;
    Rasterizer      rasterizer;

    public ExampleRectifier(Config config, String imagepath) throws IOException
    {
        ////////////////////////////////////////
        // Get output image path
        String toks[] = imagepath.split("\\.");
        String newpath = toks[0];
        for (int i=1; i+1 < toks.length; i++)
            newpath = String.format("%s.%s", newpath, toks[i]);
        newpath = String.format("%s.rectified.%s", newpath, toks[toks.length-1]);
        System.out.printf("Output image path: '%s'\n", newpath);

        ////////////////////////////////////////
        // Load image
        System.out.println("Reading input image");
        BufferedImage in = ImageIO.read(new File(imagepath));
        in = ImageConvert.convertImage(in, BufferedImage.TYPE_INT_RGB);

        ////////////////////////////////////////
        // Input view
        System.out.println("Creating camera calibration");
        input = new SimpleCaltechCalibration(config);
        //input = new ScaledView(0.5, input);

        ////////////////////////////////////////
        // Output view
        System.out.println("Creating rectified view");
        output = new MaxRectifiedView(input);
        //output = new MaxInscribedRectifiedView(input);
        //output = new ScaledView(2, output);

        ////////////////////////////////////////
        // Make rasterizer
        System.out.println("Creating rasterizer");
        //rasterizer = new BilinearRasterizer(input, output);
        rasterizer = new NearestNeighborRasterizer(input, output);

        ////////////////////////////////////////
        // Rasterize image
        System.out.println("Rasterizing image");
        BufferedImage out = rasterizer.rectifyImage(in);

        ////////////////////////////////////////
        // Write image
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
