package april.jcam;

import java.util.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.io.*;

import javax.swing.*;
import javax.imageio.*;

import april.util.*;
import april.vis.*;
import april.jmat.*;

public class ISLogViewer
{
    JFrame jf;
    VisWorld vw;
    VisCanvas vc;

    VisWorld.Buffer vbim;
    VisWorld.Buffer vbhud;

    ParameterGUI pg;
    JPanel paramsPanel;

    ISLog reader;
    long sys_t0;
    long log_t0;

    boolean once = true;

    public static void main(String args[])
    {
        if (args.length != 1) {
            System.err.println("Usage: <log file path>");
            System.exit(-1);
        }

        new ISLogViewer(args[0]);
    }

    public ISLogViewer(String filename)
    {
        try {
            reader = new ISLog(filename, "r");
        } catch (IOException ex) {
            System.err.println("ERR: Could not create ISLog file");
            System.exit(-1);
        }

        setupPG();
        setupVis();
        setupGUI();

        ISLog.ISEvent e;
        try {
            e = reader.readNext();
            log_t0 = e.utime;
            sys_t0 = TimeUtil.utime();
        } catch (IOException ex) {
            System.err.println("ERR: Could not get first image.");
            System.exit(-1);
        }

        new ViewThread().start();
    }

    private void setupPG()
    {
        pg = new ParameterGUI();
        //pg.addListener(this);
    }

    private void setupVis()
    {
        vw = new VisWorld();
        vc = new VisCanvas(vw);

        vc.getViewManager().viewGoal.perspectiveness = 0;
        vc.setBackground(Color.black);

        vbim  = vw.getBuffer("images");
        vbhud = vw.getBuffer("hud");
    }

    private void setupGUI()
    {
        paramsPanel = new EnabledBuffersPanel(vc);

        jf = new JFrame("ISLogViewer");
        jf.setLayout(new BorderLayout());

        JSplitPane jspv = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                                         vc,
                                         pg);
        jspv.setDividerLocation(1.0);
        jspv.setResizeWeight(1.0);

        JSplitPane jsph = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, jspv, paramsPanel);
        jsph.setDividerLocation(1.0);
        jsph.setResizeWeight(1.0);

        jf.add(jsph, BorderLayout.CENTER);

        // full screen, baby!
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        device.setFullScreenWindow(jf);

        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);
    }

    private class ViewThread extends Thread
    {
        public void run()
        {
            ISLog.ISEvent e;

            while (true) {
                try {
                    e = reader.readNext();
                } catch (IOException ex) {
                    System.out.println("WRN: End of ISLog reached. Exception: "+ex);
                    break;
                }

                long now = TimeUtil.utime();
                int ms   = (int) (((e.utime - log_t0) - (now - sys_t0)) * 1e-3);

                if (ms > 0)
                    TimeUtil.sleep(ms);

                plotFrame(e);
            }
        }
    }

    public void plotFrame(ISLog.ISEvent e)
    {
        // Image
        BufferedImage im = ImageConvert.convertToImage(e.ifmt.format, e.ifmt.width,
                                                       e.ifmt.height, e.buf);
        double XY0[] = new double[] {0, 0};
        double XY1[] = new double[] {im.getWidth(), im.getHeight()};

        vbim.addBuffered(new VisLighting(false, new VisImage(new VisTexture(im),
                                                             XY0, XY1,
                                                             true)));

        // HUD
        String str =    "" +
                        "<<mono-normal,left>>ELAPSED:     %14.3f s\n" +
                        "<<mono-normal,left>>POSITION:    %15.1f%%\n" +
                        "<<mono-normal,left>>UTIME:       %16d\n" +
                        "<<mono-normal,left>>BYTE OFFSET: %16d";

        double position = 0;
        try {
            position = reader.getPositionFraction();
        } catch (IOException ex) {}

        vbhud.addBuffered(new VisText(VisText.ANCHOR.TOP_LEFT,
                                      String.format(str,
                                                    (e.utime - log_t0)*1e-6,
                                                    100*position,
                                                    e.utime,
                                                    e.byteOffset)));

        if (once) {
            once = false;
            vc.getViewManager().viewGoal.fit2D(XY0, XY1);
        }

        // switch
        vbim.switchBuffer();
        vbhud.switchBuffer();
    }
}
