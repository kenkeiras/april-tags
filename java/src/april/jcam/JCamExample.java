package april.jcam;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import javax.swing.*;
import april.util.JImage;

// Bare-bones example class for reading images using jcam
public class JCamExample
{
    JFrame jf = new JFrame("JCam Example");
    JImage jim = new JImage();

    ImageSource isrc;

    volatile boolean running = true;

    public JCamExample(ImageSource _isrc)
    {
        isrc = _isrc;

        // Initialize GUI
        jim.setFit(true);

        jf.add(jim);
        jf.setSize(800, 600 + 22);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run()
            {
                running = false;
            }
        });
    }

    public void run()
    {
        isrc.start();

        while (running) {
            FrameData frmd = isrc.getFrame(); // wait for the next frame

            // If something goes wrong with the camera, the frame data can be null
            if (frmd == null) {
                System.out.println("ERR: Image stream interrupted!");
                break;
            }

            // Convert the raw data to a buffered image, display it
            BufferedImage im = ImageConvert.convertToImage(frmd);
            jim.setImage(im);
        }

        isrc.stop();
    }

    public static void main(String args[])
    {
        try {
            new JCamExample(ImageSource.make(args[0])).run();
        } catch (IOException ex) {
            System.err.println("ERR: Failed to create image source: " + ex);
            System.err.println("     Usage: java april.jcam.JCamExample <image source url>");
            System.exit(-1);
        }
    }
}
