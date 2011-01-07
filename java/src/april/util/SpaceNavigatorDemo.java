package april.util;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.io.*;

import april.vis.*;
import april.jmat.*;

public class SpaceNavigatorDemo implements SpaceNavigator.Listener
{
    JFrame jf;

    VisWorld.Buffer vb;
    VisWorld vw;
    VisCanvas vc;

    ParameterGUI pg;

    double translate_scale = 0.001;
    double rotate_scale = 0.0001;

    Color colors[] = new Color[] {Color.black, Color.red, Color.yellow,
                                  Color.green, Color.blue};
    int colorset = 0;

    long last = 0;

    public SpaceNavigatorDemo()
    {
        // init vis
        pg = new ParameterGUI();

        vw = new VisWorld();
        vc = new VisCanvas(vw);
        vc.getViewManager().setInterfaceMode(3);

        vb = vw.getBuffer("main");
        vw.getBuffer("grid").addFront(new VisGrid());
        vw.getBuffer("axes").addFront(new VisAxes());

        jf = new JFrame("SpaceNavigator Demo");
        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);
        jf.add(pg, BorderLayout.SOUTH);

        jf.setSize(1000, 600);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);

        redraw();
    }

    @Override
    public void handleUpdate(SpaceNavigator.MotionEvent me)
    {
        long now = TimeUtil.utime();
        double dt = (now - last) / 1.0E6;
        last = now;
        vw.getBuffer("FPS").addBuffered(new VisText(VisText.ANCHOR.TOP_LEFT,
                                                    String.format("FPS: %3.1f",
                                                                  1.0/dt)));
        vw.getBuffer("FPS").switchBuffer();


        VisView vg = vc.getViewManager().viewGoal;

        // get positions of camera and focus point
        double eye[]    = LinAlg.copy(vg.eye);
        double lookAt[] = LinAlg.copy(vg.lookAt);
        double up[]     = LinAlg.copy(vg.up);

        vg.lookAt(eye,
                  lookAt,
                  vg.up);

        // compute view_0
        double view0[] = LinAlg.subtract(lookAt, eye);

        // compute unit vectors for in-view coordinate frame
        double x_norm[] = LinAlg.normalize(view0);
        double z_norm[] = LinAlg.normalize(up);
        double y_norm[] = LinAlg.crossProduct(z_norm, x_norm);

        // translate eye_0 to eye_1 with the scaled tx/ty/tz from the SpaceNavigator
        double A[][] = LinAlg.transpose(new double[][] { x_norm,
                                                         y_norm,
                                                         z_norm });
        double trans[] = LinAlg.matrixAB(A, new double[] {me.x * translate_scale,
                                                          me.y * translate_scale,
                                                          me.z * translate_scale});

        double eye1[] = LinAlg.add(eye, trans);

        // rotate view_0 and up_0 with scaled r/p/y from the SpaceNavigator *in the
        // coordinate frame designated by view_0 and up_0
        double R[][] = LinAlg.rollPitchYawToMatrix(new double[] {me.roll * rotate_scale,
                                                                 me.pitch * rotate_scale,
                                                                 me.yaw * rotate_scale});

        double B[][] = LinAlg.matrixAB(A, LinAlg.select(R, 0, 2, 0, 2));

        // update lookAt and up
        double Bt[][] = LinAlg.transpose(B);
        double view1[] = Bt[0];
        double up1[] = Bt[2];

        double lookAt1[] = LinAlg.add(eye1, view1);

        // set view parameters
        vg.lookAt(eye1, lookAt1, up1);

        // draw lookat sphere

        if (me.left)
            colorset--;

        if (me.right)
            colorset++;


        redraw();
    }

    public void redraw()
    {
        vb.addBuffered(new VisChain(LinAlg.translate(4, -5, 1),
                                    new VisBox(3, 3, 2, new VisDataFillStyle(getColor(0)))));

        vb.addBuffered(new VisChain(LinAlg.translate(4, 0, 1),
                                    new VisBox(3, 3, 4, new VisDataFillStyle(getColor(1)))));

        vb.addBuffered(new VisChain(LinAlg.translate(4, 5, 1),
                                    new VisBox(3, 2, 2, new VisDataFillStyle(getColor(2)))));

        vb.addBuffered(new VisChain(LinAlg.translate(4, 12, 1),
                                    new VisBox(4, 4, 2, new VisDataFillStyle(getColor(3)))));

        vb.addBuffered(new VisChain(LinAlg.translate(4, 17, 1),
                                    new VisBox(4, 4, 2, new VisDataFillStyle(getColor(4)))));

        // focus point
        double lookAt[] = LinAlg.copy(vc.getViewManager().viewGoal.lookAt);
        vb.addBuffered(new VisChain(LinAlg.translate(lookAt[0], lookAt[1], lookAt[2]),
                                    new VisSphere(0.05, new VisDataFillStyle(Color.red))));

        vb.switchBuffer();
    }

    public Color getColor(int offset)
    {
        int idx = ((colorset + offset) % colors.length + colors.length) % colors.length;

        return colors[idx];
    }

    public static void main(String args[])
    {
        SpaceNavigator sn = new SpaceNavigator();
        sn.addListener(new SpaceNavigatorDemo());
    }
}
