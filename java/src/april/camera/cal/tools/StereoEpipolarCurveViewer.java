package april.camera.cal.tools;

import java.util.*;
import java.io.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;

import javax.swing.*;

import april.config.*;
import april.camera.cal.*;
import april.vis.*;
import april.util.*;
import april.jmat.*;
import april.jmat.geom.*;

public class StereoEpipolarCurveViewer implements ParameterListener
{
    Config config;
    CameraSet cameras;

    JFrame jf;
    VisWorld vw;
    VisLayer vl;
    VisCanvas vc;

    VisWorld.Buffer vbleft;
    VisWorld.Buffer vbright;

    ParameterGUI pg;

    public StereoEpipolarCurveViewer(Config config)
    {
        if (config == null) {
            System.err.println("Config object is null. Exiting.");
            System.exit(-1);
        }

        this.config = config;
        this.cameras = new CameraSet(config);

        setupGUI();
    }

    private void setupGUI()
    {
        // parametergui
        pg = new ParameterGUI();
        pg.addListener(this);

        // vis
        vw = new VisWorld();
        vl = new VisLayer(vw);
        vc = new VisCanvas(vl);

        vl.addEventHandler(new EventAdapter());

        vl.cameraManager.getCameraTarget().perspectiveness = 0;
        vc.setBackground(Color.black);
        vl.cameraManager.fit2D(getXY(0, "left"), getXY(1, "right"), true);

        vbleft = vw.getBuffer("left");
        vbright = vw.getBuffer("right");

        // jframe
        jf = new JFrame("Stereo Epipolar Curve Viewer");
        jf.setLayout(new BorderLayout());

        JSplitPane jspv = new JSplitPane(JSplitPane.VERTICAL_SPLIT, vc, pg);
        jspv.setDividerLocation(1.0);
        jspv.setResizeWeight(1.0);

        jf.add(jspv, BorderLayout.CENTER);
        jf.setSize(1000,400);

        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);
    }

    public void parameterChanged(ParameterGUI pg, String name)
    {
    }

    private final class EventAdapter extends VisEventAdapter
    {
        private final double leftXY0[];
        private final double leftXY1[];
        private final double rightXY0[];
        private final double rightXY1[];

        private EventAdapter()
        {
            this.leftXY0 = getXY(0, "left");
            this.leftXY1 = getXY(1, "left");
            this.rightXY0 = getXY(0, "right");
            this.rightXY1 = getXY(1, "right");
        }

        @Override
        public boolean mouseMoved(VisCanvas vc, VisLayer vl, VisCanvas.RenderInfo rinfo, GRay3D ray, MouseEvent e)
        {
            double vis_xy[] = ray.intersectPlaneXY(0);

            // left image?
            if (vis_xy[0] >= leftXY0[0] && vis_xy[0] < leftXY1[0] &&
                vis_xy[1] >= leftXY0[1] && vis_xy[1] < leftXY1[1])
            {
                double corrected_xy[] = new double[] { vis_xy[0] - leftXY0[0],
                                                       vis_xy[1] - leftXY0[1]};

                double im_xy[] = new double[] {  vis_xy[0] - leftXY0[0] ,
                                                leftXY1[1] -  vis_xy[1] };

                redraw(vbleft, vbright,
                       leftXY0, leftXY1, rightXY0, rightXY1,
                       corrected_xy, im_xy,
                       cameras.getCalibration(0), cameras.getExtrinsicsMatrix(0),
                       cameras.getCalibration(1), cameras.getExtrinsicsMatrix(1));

                return true;
            }

            // right image?
            if (vis_xy[0] >= rightXY0[0] && vis_xy[0] < rightXY1[0] &&
                vis_xy[1] >= rightXY0[1] && vis_xy[1] < rightXY1[1])
            {
                double corrected_xy[] = new double[] { vis_xy[0] - rightXY0[0],
                                                       vis_xy[1] - rightXY0[1]};

                double im_xy[] = new double[] {   vis_xy[0] - rightXY0[0] ,
                                                rightXY1[1] -   vis_xy[1] };

                redraw(vbright, vbleft,
                       rightXY0, rightXY1, leftXY0, leftXY1,
                       corrected_xy, im_xy,
                       cameras.getCalibration(1), cameras.getExtrinsicsMatrix(1),
                       cameras.getCalibration(0), cameras.getExtrinsicsMatrix(0));

                return true;
            }

            // it wasn't in either image, so we can't do anything
            return false;
        }
    }

    private double[] getXY(int zeroone, String leftright)
    {
        int leftWidth   = cameras.getCalibration(0).getWidth();
        int leftHeight  = cameras.getCalibration(0).getHeight();
        int rightWidth  = cameras.getCalibration(1).getWidth();
        int rightHeight = cameras.getCalibration(1).getHeight();

        switch(zeroone) {
            case 0:
                if (leftright.equals("left")) {
                    // left
                    return new double[] { 0                   , 0          };
                } else {
                    // right
                    return new double[] { leftWidth           , 0          };
                }
            case 1:
                if (leftright.equals("left")) {
                    // left
                    return new double[] { leftWidth           , leftHeight };
                } else {
                    // right
                    return new double[] { leftWidth+rightWidth, rightHeight};
                }
        }

        return null;
    }

    public void redraw(VisWorld.Buffer vbactive, VisWorld.Buffer vbpassive,
                       double activeXY0[],       double activeXY1[],
                       double passiveXY0[],      double passiveXY1[],
                       double active_xy[],       double im_xy[],
                       Calibration activeCal,    double activeC2G[][],
                       Calibration passiveCal,   double passiveC2G[][])
    {
        drawBox(vbactive, activeXY0, activeXY1, Color.blue);
        drawBox(vbpassive, passiveXY0, passiveXY1, Color.red);

        vbactive.addBack(new VzPoints(new VisVertexData(LinAlg.add(activeXY0, active_xy)),
                                      new VzPoints.Style(Color.green, 8)));

        drawEpipolarCurve(vbpassive, passiveXY0, passiveXY1, im_xy,
                          activeCal, activeC2G,
                          passiveCal, passiveC2G);

        vbactive.swap();
        vbpassive.swap();
    }

    public void drawBox(VisWorld.Buffer vb, double XY0[], double XY1[], Color c)
    {
        ArrayList<double[]> points = new ArrayList<double[]>();
        points.add(new double[] {XY0[0], XY0[1]});
        points.add(new double[] {XY0[0], XY1[1]});
        points.add(new double[] {XY1[0], XY1[1]});
        points.add(new double[] {XY1[0], XY0[1]});

        vb.addBack(new VzLines(new VisVertexData(points),
                               VzLines.LINE_LOOP,
                               new VzLines.Style(c, 1)));
    }

    public void drawEpipolarCurve(VisWorld.Buffer vb, double XY0[], double XY1[], double xy_dp[],
                                  Calibration activeCal,    double activeC2G[][],
                                  Calibration passiveCal,   double passiveC2G[][])
    {
        ArrayList<double[]> points = new ArrayList<double[]>();

        double xy_rp[] = activeCal.rectify(xy_dp);

        double K[][] = activeCal.getIntrinsics();

        double _xy_rn[][] = LinAlg.matrixAB(LinAlg.inverse(K),
                                            new double[][] { { xy_rp[0] } ,
                                                             { xy_rp[1] } ,
                                                             {        1 } });
        double xy_rn[] = new double[] { _xy_rn[0][0] / _xy_rn[2][0] ,
                                        _xy_rn[1][0] / _xy_rn[2][0] };

        ArrayList<double[]> points_global = new ArrayList<double[]>();

        // sample depths from 0 to 100 meters
        for (double z=0.0; z < 100; z += 0.02) {

            double xyz_cam[] = new double[] { xy_rn[0]*z ,
                                              xy_rn[1]*z ,
                                                       z };

            double xyz_global[] = LinAlg.transform(activeC2G,
                                                   xyz_cam);
            points_global.add(xyz_global);

            double xy[] = passiveCal.project(LinAlg.transform(LinAlg.inverse(passiveC2G),
                                                              xyz_global));

            if (xy[0] >= 0 && xy[0] < passiveCal.getWidth() &&
                xy[1] >= 0 && xy[1] < passiveCal.getHeight())
            {
                xy[1] = passiveCal.getHeight() - xy[1];
                points.add(xy);
            }
        }

        //vb.addBack(new VzLines(new VisVertexData(LinAlg.transform(LinAlg.translate(XY0[0], XY0[1], 0),
        //                                                          points)),
        //                       VzLines.LINE_STRIP,
        //                       new VzLines.Style(Color.green, 1)));
        vb.addBack(new VzPoints(new VisVertexData(LinAlg.transform(LinAlg.translate(XY0[0], XY0[1], 0),
                                                                  points)),
                                new VzPoints.Style(Color.green, 1)));
    }

    public static void main(String args[])
    {
        GetOpt opts  = new GetOpt();

        opts.addBoolean('h',"help",false,"See this help screen");
        opts.addString('c',"config","","StereoCamera config");
        opts.addString('s',"child","cameraCalibration","Camera calibration config child");

        if (!opts.parse(args)) {
            System.out.println("option error: "+opts.getReason());
	    }

        String configstr = opts.getString("config");
        String childstr = opts.getString("child");

        if (opts.getBoolean("help") || configstr.isEmpty()){
            System.out.println("Usage:");
            opts.doHelp();
            System.exit(1);
        }

        try {
            Config config = new ConfigFile(configstr);

            new StereoEpipolarCurveViewer(config.getChild(childstr));
        } catch (IOException e) {
            System.err.println("ERR: "+e);
            System.exit(-1);
        }
    }
}
