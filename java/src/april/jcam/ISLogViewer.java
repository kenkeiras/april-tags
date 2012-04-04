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

import lcm.lcm.*;
import april.lcmtypes.*;

public class ISLogViewer implements LCMSubscriber
{
    JFrame jf;
    VisWorld vw;
    VisLayer vl;
    VisCanvas vc;

    VisWorld.Buffer vbim;
    VisWorld.Buffer vbhud;

    ParameterGUI pg;
    JPanel paramsPanel;

    ISLog log;
    long sys_t0;
    long log_t0;

    LCM lcm = LCM.getSingleton();
    String logdir;
    String channel;

    boolean once = true;

    public static void main(String args[])
    {
        if (args.length == 1) {
            new ISLogViewer(args[0]);
        } else if (args.length == 2) {
            new ISLogViewer(args[0], args[1]);
        } else {
            System.err.println("Usage (basic):     <log path>");
            System.err.println("Usage (LCM+ISLog): <log directory> <lcm channel>");
        }
    }

    public ISLogViewer(String filename)
    {
        System.out.printf("Using ISLog '%s'\n", filename);
        try {
            log = new ISLog(filename, "r");
        } catch (IOException ex) {
            System.err.println("ERR: Could not create ISLog file");
            System.exit(-1);
        }

        setupPG();
        setupVis();
        setupGUI();

        ISLog.ISEvent e;
        try {
            e = log.readNext();
            log_t0 = e.utime;
            sys_t0 = TimeUtil.utime();
        } catch (IOException ex) {
            System.err.println("ERR: Could not get first image.");
            System.exit(-1);
        }

        new ViewThread().start();
    }

    public ISLogViewer(String logdir, String channel)
    {
        this.logdir = logdir;
        this.channel = channel;

        System.out.printf("Using log directory '%s'\n", logdir);
        System.out.printf("Using LCM Channel '%s' for playback control\n", channel);

        setupPG();
        setupVis();
        setupGUI();

        lcm.subscribe(this.channel, this);
    }

    private void setupPG()
    {
        pg = new ParameterGUI();
        //pg.addListener(this);
    }

    private void setupVis()
    {
        vw = new VisWorld();
        vl = new VisLayer(vw);
        vc = new VisCanvas(vl);

        //vis2 vl.cameraManager.perspectiveness1 = 0;
        vl.backgroundColor = Color.black;

        vbim  = vw.getBuffer("images");
        vbhud = vw.getBuffer("hud");
    }

    private void setupGUI()
    {
        paramsPanel = new JPanel();//vis2 new EnabledBuffersPanel(vc);

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
                    e = log.readNext();
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

    public ISLog ensureLogOpen(URLParser up, ISLog currentLog) throws IOException
    {
        String protocol = up.get("protocol");
        if (protocol == null)
            throw new IOException();

        String location = up.get("network");
        if (location == null)
            throw new IOException();

        String path = logdir + location;
        // close old log if it's time for the new one
        if (currentLog != null && !path.equals(currentLog.getPath())) {
            currentLog.close();
            currentLog = null;
        }

        // we don't have a log! open one!
        if (currentLog == null) {
            System.out.printf("Opening log at path '%s'\n", path);
            return new ISLog(path, "r");
        }

        return currentLog;
    }

    public synchronized void messageReceived(LCM lcm, String channel, LCMDataInputStream ins)
    {
        try {
            if (channel.equals(this.channel)) {
                url_t url = new url_t(ins);
                URLParser up = new URLParser(url.url);

                log = ensureLogOpen(up, log);

                String offset = up.get("offset");
                if (offset == null)
                    return;

                ISLog.ISEvent e = log.readAtPosition(Long.valueOf(offset));

                plotFrame(e);
            }
        } catch (IOException ex) {
            System.err.println("Exception: "+ex);
        }
    }

    public void plotFrame(ISLog.ISEvent e)
    {
        // Image
        BufferedImage im = ImageConvert.convertToImage(e.ifmt.format, e.ifmt.width,
                                                       e.ifmt.height, e.buf);

        vbim.addBack(new VisLighting(false, new VisChain(LinAlg.scale(1,-1,1), new VzImage(im))));

        // HUD
        String str =    "" +
            "<<mono-normal,left>>ELAPSED:     %14.3f s\n" +
            "<<mono-normal,left>>POSITION:    %15.1f%%\n" +
            "<<mono-normal,left>>UTIME:       %16d\n" +
            "<<mono-normal,left>>BYTE OFFSET: %16d";

        double position = 0;
        try {
            position = log.getPositionFraction();
        } catch (IOException ex) {}

        vbhud.addBack(new VisPixCoords(VisPixCoords.ORIGIN.TOP_LEFT,
                                              new VzText(VzText.ANCHOR.TOP_LEFT,
                                                          String.format(str,
                                                                        (e.utime - log_t0)*1e-6,
                                                                        100*position,
                                                                        e.utime,
                                                                        e.byteOffset))));

        if (once) {
            once = false;
            vl.cameraManager.fit2D(new double[2], new double[]{e.ifmt.width,e.ifmt.height}, true);
        }

        // switch
        vbim.swap();
        vbhud.swap();
    }
}
