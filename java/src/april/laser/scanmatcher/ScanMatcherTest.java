package april.laser.scanmatcher;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import javax.swing.*;
import java.util.*;

import april.config.*;
import april.jmat.*;
import april.util.*;
import april.vis.*;
import april.graph.*;
import april.lcmtypes.*;

import lcm.lcm.*;

public class ScanMatcherTest implements LCMSubscriber, ParameterListener
{
    Config      config;
    ScanMatcher scanMatcher;

    JFrame jf;
    VisWorld vw = new VisWorld();
    VisCanvas vc = new VisCanvas(vw);

    LCM lcm = LCM.getSingleton();
    ParameterGUI pg = new ParameterGUI();

    ArrayList<pose_t> poses = new ArrayList<pose_t>();

    public static void main(String args[])
    {
        Config config = ConfigUtil.getDefaultConfig(args);

        new ScanMatcherTest(config);
    }

    public ScanMatcherTest(Config config)
    {
        this.config = config;

        pg.addButtons("clear", "clear");
        pg.addListener(this);

        jf = new JFrame("ScanMatcherTest");
        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);
        jf.add(pg.getPanel(), BorderLayout.SOUTH);
        jf.setSize(600,400);
        jf.setVisible(true);

        lcm.subscribe("LIDAR.*", this);
        lcm.subscribe("LASER.*", this);
        lcm.subscribe("POSE", this);

        scanMatcher = new ScanMatcher(config.getChild("scanmatcher"));
      }

    public void parameterChanged(ParameterGUI pg, String name)
    {
        if (name.equals("clear")) {
            scanMatcher = new ScanMatcher(config.getChild("scanmatcher"));
            vw.clear();
        }
    }

    public void messageReceived(LCM lcm, String channel, LCMDataInputStream ins)
    {
        try {
            messageReceivedEx(channel, ins);
        } catch (IOException ex) {
            System.out.println("ex: "+ex);
        }
    }

    void messageReceivedEx(String channel, LCMDataInputStream ins) throws IOException
    {
        ///////////////////////////////////////////////////////////
        if (channel.equals("POSE")) {
            pose_t p = new pose_t(ins);
            poses.add(p);

            pose_t lastPose = poses.get(poses.size()-1);

            double lastxyt[] = poseToXyt(lastPose);
            double nowxyt[] = poseToXyt(p);

            double odomT[] = LinAlg.xytInvMul31(lastxyt, nowxyt);
            scanMatcher.processOdometry(odomT, null);

            return;
        }

       ///////////////////////////////////////////////////////////
        if (channel.startsWith("LASER") || channel.startsWith("LIDAR")) {
            laser_t ldata = new laser_t(ins);

            //////////////////////////////
            // Project the points
            ArrayList<double[]> points = new ArrayList<double[]>();

            double maxRange = config.getRoot().getDouble(channel+".max_range", Double.MAX_VALUE);

            for (int i = 0; i < ldata.nranges; i++) {
                double theta = ldata.rad0 + ldata.radstep * i;
                double r = ldata.ranges[i];

                if (r > maxRange)
                    continue;

                double p[] = new double[] {
                    r * Math.cos(theta),
                    r * Math.sin(theta) };

                points.add(p);
            }

            if (points.size() == 0)
                return;

            // sensor to body
            Matrix S2B = ConfigUtil.getMatrix(config.getRoot(), channel);

            ArrayList<double[]> bodyPoints = LinAlg.transform(S2B, points);

            scanMatcher.processScan(bodyPoints);

            if (true) {
                VisWorld.Buffer vb = vw.getBuffer("lastscan");
                vb.addBuffered(new VisChain(LinAlg.xytToMatrix(scanMatcher.getPosition()),
                                            new VisRobot(Color.red),
                                            new VisData(new VisDataPointStyle(Color.red, 2),
                                                        bodyPoints)));
                vb.switchBuffer();
            }

            if (true) {
                VisWorld.Buffer vb = vw.getBuffer("raster");
                vb.setDrawOrder(-100);
                GridMap gm = scanMatcher.getGridMap();
                if (gm != null) {
                    BufferedImage im = gm.makeBufferedImage();
                    vb.addBuffered(new VisImage(new VisTexture(im), gm.getXY0(), gm.getXY1()));
                }
                vb.switchBuffer();
            }

            if (true) {
                VisWorld.Buffer vb = vw.getBuffer("graph");
                vb.setDrawOrder(-99);
                Graph g = scanMatcher.getGraph();

                for (GNode gn : g.nodes) {
                    ArrayList<double[]> p = (ArrayList<double[]>) gn.getAttribute("points");
                    vb.addBuffered(new VisChain(LinAlg.xytToMatrix(gn.state),
                                                new VisRobot(Color.blue),
                                                new VisData(new VisDataPointStyle(Color.blue, 1),
                                                            p)));
                }

                vb.switchBuffer();
            }
        }

    }


    static double[] poseToXyt(pose_t p)
    {
        double rpy[] = LinAlg.quatToRollPitchYaw(p.orientation);
        return new double[] { p.pos[0], p.pos[1], rpy[2] };
    }
 }
