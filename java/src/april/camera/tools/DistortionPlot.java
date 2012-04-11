package april.camera.tools;

import java.io.*;
import java.util.*;
import java.awt.*;

import javax.swing.*;

import april.config.*;
import april.camera.*;
import april.jmat.*;
import april.vis.*;
import april.util.*;

public class DistortionPlot
{
    JFrame jf;
    JPanel paramsPanel;
    ParameterGUI pg;

    VisWorld vw;
    VisLayer vl;
    VisCanvas vc;
    VisWorld.Buffer vb;

    Config config;
    Calibration cal;
    SyntheticView view;

    int stepsize = 2;

    public DistortionPlot(Config config)
    {
        this.config = config;

        // XXX only use first camera
        cal = new CameraSet(config).getCalibration(0);
        view = new MaxInscribedRectifiedView(cal);

        setupGUI();

        plot();
    }

    private void plot()
    {
        int width = cal.getWidth();
        int height = cal.getHeight();

        double XY0[] = new double[] {     0,      0 };
        double XY1[] = new double[] { width, height };

        {
            double minx = Double.MAX_VALUE;
            double maxx = Double.MIN_VALUE;
            double miny = Double.MAX_VALUE;
            double maxy = Double.MIN_VALUE;

            double max_error = 0;

            ArrayList<double[]> points_d1 = new ArrayList<double[]>();
            ArrayList<double[]> points_r1 = new ArrayList<double[]>();
            ArrayList<double[]> points_d2 = new ArrayList<double[]>();

            for (int y=0; y < height; y+=stepsize) {
                for (int x=0; x < width; x+=stepsize) {

                    double xy1_dp[] = new double[] {x, y};
                    double xy1_rp[] = cal.rectify(xy1_dp);
                    double xy2_dp[] = cal.distort(xy1_rp);

                    points_d1.add(xy1_dp);
                    points_r1.add(xy1_rp);
                    points_d2.add(xy2_dp);

                    double error = LinAlg.distance(xy1_dp, xy2_dp);
                    max_error = Math.max(error, max_error);

                    minx = Math.min(xy1_dp[0], Math.min(xy1_rp[0], Math.min(xy2_dp[0], minx)));
                    maxx = Math.max(xy1_dp[0], Math.max(xy1_rp[0], Math.max(xy2_dp[0], maxx)));

                    miny = Math.min(xy1_dp[1], Math.min(xy1_rp[1], Math.min(xy2_dp[1], miny)));
                    maxy = Math.max(xy1_dp[1], Math.max(xy1_rp[1], Math.max(xy2_dp[1], maxy)));
                }
            }

            // HUD
            vb = vw.getBuffer("HUD");
            vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.TOP_LEFT,
                                        new VzText(VzText.ANCHOR.TOP_LEFT,
                                                   String.format("<<monospaced-12>>Max error: %f",
                                                                 max_error))));
            vb.swap();

            // D1
            vb = vw.getBuffer("1a. Points (distorted)");
            vb.addBack(new VisChain(LinAlg.scale(1, -1, 1),
                                        new VzPoints(new VisVertexData(points_d1),
                                                     new VzPoints.Style(Color.blue, 1))));
            vb.swap();

            // R1
            vb = vw.getBuffer("1b. Points (distorted -> rectified)");
            vb.addBack(new VisChain(LinAlg.scale(1, -1, 1),
                                        new VzPoints(new VisVertexData(points_r1),
                                                     new VzPoints.Style(Color.red, 1))));
            vb.swap();

            // D2
            vb = vw.getBuffer("1c. Points (distorted -> rectified -> distorted)");
            vb.addBack(new VisChain(LinAlg.scale(1, -1, 1),
                                        new VzPoints(new VisVertexData(points_d2),
                                                     new VzPoints.Style(Color.green, 1))));
            vb.swap();

            vl.cameraManager.fit2D(new double[] {minx, Math.min(-miny, -maxy)},
                                   new double[] {maxx, Math.max(-miny, -maxy)},
                                   true);
        }

        {
            ArrayList<double[]> points_r1 = new ArrayList<double[]>();
            ArrayList<double[]> points_d1 = new ArrayList<double[]>();

            for (int y = -height; y < height*2; y+=stepsize) {
                for (int x = -width; x < width*2; x+=stepsize) {

                    double xy_rp[] = new double[] {x, y};
                    double xy_dp[] = cal.distort(xy_rp);

                    int ix = (int) Math.floor(xy_dp[0]);
                    int iy = (int) Math.floor(xy_dp[1]);

                    if (ix < 0 || ix >= width || iy < 0 || iy >= height)
                        continue;

                    points_r1.add(xy_rp);
                    points_d1.add(xy_dp);
                }
            }

            // R2
            vb = vw.getBuffer("2a. Points (rectified)");
            vb.addBack(new VisChain(LinAlg.scale(1, -1, 1),
                                        new VzPoints(new VisVertexData(points_r1),
                                                     new VzPoints.Style(Color.cyan, 1))));
            vb.swap();

            // D3
            vb = vw.getBuffer("2b. Points (rectified -> distorted)");
            vb.addBack(new VisChain(LinAlg.scale(1, -1, 1),
                                        new VzPoints(new VisVertexData(points_d1),
                                                     new VzPoints.Style(Color.magenta, 1))));
            vb.swap();
        }

        {
            ArrayList<double[]> points_r1 = new ArrayList<double[]>();
            ArrayList<double[]> points_d1 = new ArrayList<double[]>();

            int viewHeight = view.getHeight();
            int viewWidth  = view.getWidth();

            for (int y=0; y < viewHeight; y+=stepsize) {
                for (int x=0; x < viewWidth; x+=stepsize) {

                    double xy_rp[] = new double[] { x, y };
                    double xy_dp[] = view.distort(xy_rp);

                    points_r1.add(xy_rp);
                    points_d1.add(xy_dp);
                }
            }

            // R
            vb = vw.getBuffer("3a. Points (max rectified)");
            vb.addBack(new VisChain(LinAlg.scale(1, -1, 1),
                                        new VzPoints(new VisVertexData(points_r1),
                                                     new VzPoints.Style(Color.orange, 1))));
            vb.swap();

            // D
            vb = vw.getBuffer("3b. Points (max rectified -> distorted)");
            vb.addBack(new VisChain(LinAlg.scale(1, -1, 1),
                                        new VzPoints(new VisVertexData(points_d1),
                                                     new VzPoints.Style(Color.blue, 1))));
            vb.swap();
        }

        {
            ArrayList<double[]> points_r1 = new ArrayList<double[]>();
            ArrayList<double[]> points_d1 = new ArrayList<double[]>();

            int viewHeight = view.getHeight();
            int viewWidth  = view.getWidth();

            for (int y=0; y < height; y+=stepsize) {
                for (int x=0; x < width; x+=stepsize) {

                    double xy_dp[] = new double[] { x, y };
                    double xy_rp[] = view.rectify(xy_dp);

                    points_d1.add(xy_dp);
                    points_r1.add(xy_rp);
                }
            }

            // D
            vb = vw.getBuffer("4a. Points (distorted)");
            vb.addBack(new VisChain(LinAlg.scale(1, -1, 1),
                                        new VzPoints(new VisVertexData(points_d1),
                                                     new VzPoints.Style(Color.pink, 1))));
            vb.swap();

            // R
            vb = vw.getBuffer("4b. Points (distorted -> max rectified)");
            vb.addBack(new VisChain(LinAlg.scale(1, -1, 1),
                                        new VzPoints(new VisVertexData(points_r1),
                                                     new VzPoints.Style(Color.green, 1))));
            vb.swap();
        }
    }

    private void setupGUI()
    {
        // vis
        vw = new VisWorld();
        vl = new VisLayer(vw);
        vc = new VisCanvas(vl);
        vl.backgroundColor = Color.black;

        // gui tools
        pg = new ParameterGUI();
        LayerBufferPanel bufferPanel = new LayerBufferPanel(vc);

        // jframe
        jf = new JFrame("Distortion plot");
        jf.setLayout(new BorderLayout());

        JSplitPane jspv = new JSplitPane(JSplitPane.VERTICAL_SPLIT, vc, pg);
        jspv.setDividerLocation(1.0);
        jspv.setResizeWeight(1.0);

        JSplitPane jsph = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, jspv, bufferPanel);
        jsph.setDividerLocation(0.9);
        jsph.setResizeWeight(0.9);

        jf.add(jsph, BorderLayout.CENTER);
        jf.setSize(1200, 700);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);
    }

    public static void main(String args[])
    {
        GetOpt opts  = new GetOpt();

        opts.addBoolean('h',"help",false,"See this help screen");
        opts.addString('c',"config","","Config file path");
        opts.addString('s',"child","","Child string");

        if (!opts.parse(args)) {
            System.out.println("Option error: "+opts.getReason());
	    }

        String configstr = opts.getString("config");
        String childstr  = opts.getString("child");

        if (opts.getBoolean("help") || configstr.isEmpty() || childstr.isEmpty()){
            System.out.println("Usage:");
            opts.doHelp();
            System.exit(1);
        }

        try {
            Config config = new ConfigFile(configstr);

            new DistortionPlot(config.getChild(childstr));

        } catch (IOException ex) {
            System.out.println(ex);
        }
    }
}
