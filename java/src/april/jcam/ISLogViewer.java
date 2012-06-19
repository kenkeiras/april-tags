package april.jcam;

import java.util.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.io.*;

import javax.swing.*;
import javax.imageio.*;

import april.jmat.*;
import april.util.*;
import april.vis.*;

import lcm.lcm.*;
import april.lcmtypes.*;

public class ISLogViewer
{
    JFrame jf;
    VisWorld vw;
    VisLayer vl;
    VisCanvas vc;

    ImageSource isrc;

    boolean once = true;

    public static void main(String args[])
    {
        if (args.length != 1) {
            System.err.println("Usage: <image source url>");
            System.exit(-1);
        }

        // this application is intended for islog:// or islog-lcm:// camera urls
        assert(args[0].startsWith("islog"));

        new ISLogViewer(args[0]);
    }

    public ISLogViewer(String url)
    {
        try {
            isrc = ImageSource.make(url);
        } catch (IOException ex) {
            System.err.println("ISLogViewer caught exception while creating image source: " + ex);
            System.exit(-1);
        }

        setupGUI();

        while (true) {
            byte buf[] = isrc.getFrame();

            if (buf == null) {
                TimeUtil.sleep(10);
                continue;
            }

            ImageSourceFormat ifmt = isrc.getFormat(isrc.getCurrentFormatIndex());

            long utime = TimeUtil.utime();
            if (isrc instanceof ImageSourceISLogLCM)
                utime = ((ImageSourceISLogLCM) isrc).getTimestamp();

            plotFrame(buf, ifmt, utime);
        }
    }

    private void setupGUI()
    {
        vw = new VisWorld();
        vl = new VisLayer(vw);
        vc = new VisCanvas(vl);

        vl.backgroundColor = Color.black;

        jf = new JFrame("ISLogViewer");
        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);

        jf.setSize(800, 600);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);
    }

    public void plotFrame(byte buf[], ImageSourceFormat ifmt, long utime)
    {
        // Image
        BufferedImage im = ImageConvert.convertToImage(ifmt.format, ifmt.width,
                                                       ifmt.height, buf);

        VisWorld.Buffer vbim  = vw.getBuffer("images");
        vbim.addBack(new VisLighting(false, new VzImage(im, VzImage.FLIP)));

        if (once) {
            once = false;
            vl.cameraManager.fit2D(new double[2], new double[]{ifmt.width, ifmt.height}, true);
        }

        // switch
        vbim.swap();
    }
}

