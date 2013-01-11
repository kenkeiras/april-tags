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

public class DistortionDebugger implements ParameterListener
{
    JFrame jf;
    JPanel paramsPanel;
    ParameterGUI pg;

    VisWorld vw;
    VisLayer vl;
    VisCanvas vc;
    VisWorld.Buffer vb;

    double  f;
    int     width;
    int     height;
    double  cx;
    double  cy;
    int     rmax;
    int     iterations;

    public DistortionDebugger()
    {
        f           = 650;
        width       = 752;
        height      = 480;
        cx          = width/2;
        cy          = height/2;
        rmax        = (int) Math.sqrt(Math.pow(width-cx, 2) + Math.pow(height-cy, 2));
        iterations  = 10;

        setupGUI();
        plot();
    }

    private void setupGUI()
    {
        // vis
        vw = new VisWorld();
        vl = new VisLayer(vw);
        vc = new VisCanvas(vl);

        vl.backgroundColor = Color.black;
        VzGrid.addGrid(vw);

        // gui tools
        pg = new ParameterGUI();
        pg.addDoubleSlider("kc1","KC1",-1, 1, -0.4);
        pg.addDoubleSlider("kc2","KC2",-1, 1,  0.1);
        pg.addDoubleSlider("kc3","KC3",-1, 1,  0.0);
        pg.addDoubleSlider("d","Distorted pixel (radius)", 0, rmax, 0);
        pg.addListener(this);

        // jframe
        jf = new JFrame("Distortion plot");
        jf.setLayout(new BorderLayout());

        JSplitPane jspv = new JSplitPane(JSplitPane.VERTICAL_SPLIT, vc, pg);
        jspv.setDividerLocation(1.0);
        jspv.setResizeWeight(1.0);

        jf.add(jspv, BorderLayout.CENTER);
        jf.setSize(1000, 700);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);
    }

    private void plot()
    {
        ArrayList<double[]> points = new ArrayList<double[]>();
        ArrayList<double[]> multipliers = new ArrayList<double[]>();
        ArrayList<double[]> chain = new ArrayList<double[]>();
        double maxdist = 0;
        for (int i=0; i < 4*rmax; i++) {

            double r = i / f;

            double d = distort(r);
            double m = getMultiplier(r);

            double dm = getMultiplierDerivative(r);
            double c = m + r * dm;

            points.add(new double[] { r, d });
            multipliers.add(new double[] { r, m });
            chain.add(new double[] { r, c });

            maxdist = Math.max(maxdist, d*f);
        }

        {
            VisWorld.Buffer vb = vw.getBuffer("Background");
            vb.setDrawOrder(-100);
            vb.addBack(new VisChain(LinAlg.translate(5*rmax, rmax/2, 0),
                                    new VzRectangle(10*rmax, rmax,
                                                    new VzMesh.Style(new Color(30, 30, 30)))));
            vb.swap();
        }

        {
            VisWorld.Buffer vb = vw.getBuffer("Error");
            vb.setDrawOrder(-99);
            if (maxdist < rmax) {
                double height = rmax - maxdist;
                vb.addBack(new VisChain(LinAlg.translate(5*rmax, rmax - height/2, 0),
                                        new VzRectangle(10*rmax, height,
                                                        new VzMesh.Style(new Color(60, 0, 0)))));
            }
            vb.swap();
        }

        {
            VisWorld.Buffer vb = vw.getBuffer("Derivative of distort()");
            vb.addBack(new VisChain(LinAlg.scale(f, 100, 1),
                                    new VzLines(new VisVertexData(chain),
                                                VzLines.LINE_STRIP,
                                                new VzLines.Style(Color.cyan, 2))));
            vb.swap();
        }

        {
            VisWorld.Buffer vb = vw.getBuffer("Multiplier");
            vb.addBack(new VisChain(LinAlg.scale(f, 100, 1),
                                    new VzLines(new VisVertexData(multipliers),
                                                VzLines.LINE_STRIP,
                                                new VzLines.Style(Color.green, 2))));
            vb.swap();
        }

        {
            VisWorld.Buffer vb = vw.getBuffer("Distorted");
            vb.addBack(new VisChain(LinAlg.scale(f, f, 1),
                                    new VzLines(new VisVertexData(points),
                                                VzLines.LINE_STRIP,
                                                new VzLines.Style(Color.blue, 2))));
            vb.swap();

            vl.cameraManager.fit2D(new double[] {-rmax*0.2, -rmax*0.2},
                                   new double[] { rmax*2.0,  rmax*1.2}, true);
        }

        {
            String str = "";
            str += "<<green>>Multiplier(rectified) (rescaled x100)\n";
            str += "<<cyan>>Derivative of distort(rectified) (rescaled x100)\n";
            str += "<<blue>>Distort(rectified)\n";
            str += "<<red>>Rectify(distorted)\n";

            VisWorld.Buffer vb = vw.getBuffer("Legend");
            vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.TOP_LEFT,
                                        new VzText(VzText.ANCHOR.TOP_LEFT, str)));
            vb.swap();
        }

        {
            VisWorld.Buffer vb = vw.getBuffer("Axis labels");

            vb.addBack(new VisChain(LinAlg.rotateZ(Math.PI/2),
                                    LinAlg.translate(200, 0, 0),
                                    new VzText(VzText.ANCHOR.BOTTOM,
                                               "<<monospaced-24,white>>Distorted radius")));
            vb.addBack(new VisChain(LinAlg.translate(200, 0, 0),
                                    new VzText(VzText.ANCHOR.TOP,
                                               "<<monospaced-24,white>>Rectified radius")));

            vb.swap();
        }
    }

    public void parameterChanged(ParameterGUI pg, String name)
    {
        if (name.startsWith("kc")) {
            plot();
            vw.getBuffer("Rectified").swap();
        }

        if (name.equals("d")) {

            double d = pg.gd("d") / f;

            double rs[] = rectify(d);

            ArrayList<double[]> points = new ArrayList<double[]>();
            for (double r : rs)
                points.add(new double[] {r, d});

            VisWorld.Buffer vb = vw.getBuffer("Rectified");
            VisVertexData data = new VisVertexData(points);
            vb.addBack(new VisChain(LinAlg.scale(f, f, 1),
                                    new VzLines(data,
                                                VzLines.LINE_STRIP,
                                                new VzLines.Style(Color.red, 2)),
                                    new VzPoints(data,
                                                 new VzPoints.Style(Color.red, 5))));
            vb.swap();
        }
    }

    private double getMultiplierDerivative(double r)
    {
        double r2 = r*r;
        double r3 = r*r2;
        double r5 = r2*r3;

        return 2 * pg.gd("kc1") * r
             + 4 * pg.gd("kc2") * r3
             + 6 * pg.gd("kc3") * r5;
    }

    private double getMultiplier(double r)
    {
        double r2 = r*r;
        double r4 = r2*r2;
        double r6 = r2*r4;

        return 1 + pg.gd("kc1") * r2
                 + pg.gd("kc2") * r4
                 + pg.gd("kc3") * r6;
    }

    private double distort(double r)
    {
        double multiplier = getMultiplier(r);

        return r*multiplier;
    }

    private double[] rectify(double d)
    {
        double r = d;

        double res[] = new double[iterations+1];
        res[0] = r;

        double eps = 0.0001;

        for (int i=0; i < iterations; i++) {

            double dhat = distort(r);
            double errhat = dhat - d;

            double dpos = distort(r+eps);
            double errpos = dpos - d;

            double derr_by_dr = (errpos - errhat) / eps;
            double dr_by_derr = 1.0 / derr_by_dr;

            r = r - dr_by_derr * errhat;

            res[i+1] = r;
        }

        return res;
    }

    public static void main(String args[])
    {
        new DistortionDebugger();
    }
}
