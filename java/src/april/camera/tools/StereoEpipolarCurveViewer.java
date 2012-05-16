package april.camera.tools;

import java.util.*;
import java.io.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;

import javax.swing.*;
import javax.imageio.*;

import april.config.*;
import april.camera.*;
import april.vis.*;
import april.util.*;
import april.jmat.*;
import april.jmat.geom.*;
import april.jcam.*;

public class StereoEpipolarCurveViewer implements ParameterListener
{
    Config config;
    CameraSet cameras;
    View leftView;
    View rightView;

    JFrame jf;
    VisWorld vw;
    VisLayer vl;
    VisCanvas vc;

    VisWorld.Buffer vbleft;
    VisWorld.Buffer vbright;

    ParameterGUI pg;

    public StereoEpipolarCurveViewer(Config config, String leftPath, String rightPath)
    {
        if (config == null) {
            System.err.println("Config object is null. Exiting.");
            System.exit(-1);
        }

        this.config = config;
        this.cameras = new CameraSet(config);

        leftView = cameras.getCalibration(0);
        rightView = cameras.getCalibration(1);

        setupGUI();

        showImages(leftPath, rightPath);
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

    private BufferedImage loadImage(String path) throws IOException
    {
        BufferedImage im = ImageIO.read(new File(path));
        im = ImageConvert.convertImage(im, BufferedImage.TYPE_INT_RGB);

        return im;
    }

    private void showImages(String leftPath, String rightPath)
    {
        VisWorld.Buffer vb = vw.getBuffer("Images");
        vb.setDrawOrder(-100);

        try {
            double XY0[] = getXY(0, "left");
            double XY1[] = getXY(1, "left");
            BufferedImage im = loadImage(leftPath);
            vb.addBack(new VisChain(LinAlg.translate(XY0[0], XY0[1], 0),
                                    LinAlg.scale((XY1[0]-XY0[0])/im.getWidth(),
                                                 (XY1[1]-XY0[1])/im.getHeight(), 1),
                                    new VzImage(im, VzImage.FLIP)));

        } catch (IOException ex) {
        }

        try {
            double XY0[] = getXY(0, "right");
            double XY1[] = getXY(1, "right");
            BufferedImage im = loadImage(rightPath);
            vb.addBack(new VisChain(LinAlg.translate(XY0[0], XY0[1], 0),
                                    LinAlg.scale((XY1[0]-XY0[0])/im.getWidth(),
                                                 (XY1[1]-XY0[1])/im.getHeight(), 1),
                                    new VzImage(im, VzImage.FLIP)));

        } catch (IOException ex) {
        }

        vb.swap();
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
                       leftView, cameras.getExtrinsicsL2C(0),
                       rightView, cameras.getExtrinsicsL2C(1));

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
                       rightView, cameras.getExtrinsicsL2C(1),
                       leftView, cameras.getExtrinsicsL2C(0));

                return true;
            }

            // it wasn't in either image, so we can't do anything
            return false;
        }
    }

    private double[] getXY(int zeroone, String leftright)
    {
        int leftWidth   = leftView.getWidth();
        int leftHeight  = leftView.getHeight();
        int rightWidth  = rightView.getWidth();
        int rightHeight = rightView.getHeight();

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
                       View activeCal,    double activeG2C[][],
                       View passiveCal,   double passiveG2C[][])
    {
        drawBox(vbactive, activeXY0, activeXY1, Color.blue);
        drawBox(vbpassive, passiveXY0, passiveXY1, Color.red);

        vbactive.addBack(new VzPoints(new VisVertexData(LinAlg.add(activeXY0, active_xy)),
                                      new VzPoints.Style(Color.green, 4)));

        drawEpipolarCurve(vbpassive, passiveXY0, passiveXY1, im_xy,
                          activeCal, activeG2C,
                          passiveCal, passiveG2C);

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
                                  View activeCal,    double activeG2C[][],
                                  View passiveCal,   double passiveG2C[][])
    {
        double xy_rn[] = activeCal.pixelsToNorm(xy_dp);

        // sample depths from 0 to zmax meters
        double zmax = 10;
        ArrayList<double[]> points = new ArrayList<double[]>();
        ArrayList<double[]> depths = new ArrayList<double[]>();
        for (double z=0.0; z < zmax; z += 0.02) {

            double xyz_cam[] = new double[] { xy_rn[0]*z ,
                                              xy_rn[1]*z ,
                                                       z };

            double xyz_global[] = LinAlg.transform(LinAlg.inverse(activeG2C),
                                                   xyz_cam);

            double xy[] = CameraMath.project(passiveCal, passiveG2C, xyz_global);

            if (xy[0] >= 0 && xy[0] < passiveCal.getWidth() &&
                xy[1] >= 0 && xy[1] < passiveCal.getHeight())
            {
                xy[1] = passiveCal.getHeight() - xy[1];
                points.add(xy);
                depths.add(new double[] {z});
            }
        }

        vb.addBack(new VzPoints(new VisVertexData(LinAlg.transform(LinAlg.translate(XY0[0], XY0[1], 0),
                                                                  points)),
                                new VzPoints.Style(ColorMapper.makeJet(0, zmax).makeColorData(depths, 0), 2)));
    }

    public static void main(String args[])
    {
        GetOpt opts  = new GetOpt();

        opts.addBoolean('h',"help",false,"See this help screen");
        opts.addString('c',"config","","StereoCamera config");
        opts.addString('s',"child","aprilCameraCalibration","Camera calibration child (e.g. aprilCameraCalibration)");
        opts.addString('l',"leftimage","","Left image path (optional)");
        opts.addString('r',"rightimage","","Right image path (optional)");

        if (!opts.parse(args)) {
            System.out.println("option error: "+opts.getReason());
	    }

        String configstr = opts.getString("config");
        String childstr = opts.getString("child");
        String leftimagepath = opts.getString("leftimage");
        String rightimagepath = opts.getString("rightimage");

        if (opts.getBoolean("help") || configstr.isEmpty()){
            System.out.println("Usage:");
            opts.doHelp();
            System.exit(1);
        }

        try {
            Config config = new ConfigFile(configstr);

            new StereoEpipolarCurveViewer(config.getChild(childstr),
                                          leftimagepath, rightimagepath);
        } catch (IOException e) {
            System.err.println("ERR: "+e);
            System.exit(-1);
        }
    }
}
