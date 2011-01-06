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

    double lookAt[];

    double translate_scale = 0.001;
    double rotate_scale = 0.0001;

    public SpaceNavigatorDemo()
    {
        // init vis
        pg = new ParameterGUI();

        vw = new VisWorld();
        vc = new VisCanvas(vw);
        vc.getViewManager().setInterfaceMode(3);
        vc.getViewManager().viewGoal.rotate(LinAlg.rollPitchYawToQuat(new double[] {0, 0, -Math.PI/2}));

        vb = vw.getBuffer("main");
        vw.getBuffer("grid").addFront(new VisGrid());

        jf = new JFrame("SpaceNavigator Demo");
        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);
        jf.add(pg, BorderLayout.SOUTH);

        jf.setSize(1000, 600);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);


        // init virtual camera pose
        lookAt = new double[3];

        new RedrawThread().start();
    }

    @Override
    public void handleUpdate(SpaceNavigator.MotionEvent me)
    {
        lookAt[0] += me.x * translate_scale;
        lookAt[1] += me.y * translate_scale;
        lookAt[2] += me.z * translate_scale;

        VisView vg = vc.getViewManager().viewGoal;

        vg.lookAt(vg.eye,
                  lookAt,
                  vg.up);

        vg.rotate(LinAlg.rollPitchYawToQuat(new double[] {rotate_scale * me.roll,
                                                          rotate_scale * me.pitch,
                                                          rotate_scale * me.yaw}));
    }

    public class RedrawThread extends Thread
    {
        public RedrawThread()
        {
        }

        public void run()
        {
            while (true) {
                redraw();

                TimeUtil.sleep(33);
            }
        }


        public void redraw()
        {
            vb.addBuffered(new VisChain(LinAlg.translate(6, 4, 1),
                                        new VisBox(3, 3, 2, new VisDataFillStyle(Color.blue))));

            vb.addBuffered(new VisChain(LinAlg.translate(-8, 2, 2),
                                        new VisBox(3, 3, 4, new VisDataFillStyle(Color.magenta))));

            vb.addBuffered(new VisChain(LinAlg.translate(-2, 6, 1),
                                        new VisBox(3, 2, 2, new VisDataFillStyle(Color.green))));

            vb.addBuffered(new VisChain(LinAlg.translate(4, 10, 1),
                                        new VisBox(4, 4, 2, new VisDataFillStyle(Color.cyan))));

            // focus point
            vb.addBuffered(new VisChain(LinAlg.translate(lookAt[0], lookAt[1], lookAt[2]),
                                        new VisSphere(0.05, new VisDataFillStyle(Color.red))));

            vb.switchBuffer();
        }
    }

    public static void main(String args[])
    {
        SpaceNavigator sn = new SpaceNavigator();
        sn.addListener(new SpaceNavigatorDemo());
    }
}
