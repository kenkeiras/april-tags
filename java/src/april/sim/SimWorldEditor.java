package april.sim;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.awt.*;
import javax.swing.*;

import april.util.*;

import april.util.*;
import april.jmat.*;
import april.vis.*;
import april.jmat.geom.*;

public class SimWorldEditor implements ParameterListener
{
    JFrame jf;
    VisWorld vw = new VisWorld();
    VisCanvas vc = new VisCanvas(vw);

    SimWorld world;

    static final double MIN_SIZE = 0.25;

    ParameterGUI pg = new ParameterGUI();

    public SimWorldEditor(SimWorld world)
    {
        this.world = world;

        pg.addButtons("save", "save");
        pg.addListener(this);

        jf = new JFrame("SimWorldEditor");
        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);
        jf.add(pg, BorderLayout.SOUTH);

        jf.setSize(800,600);
        jf.setVisible(true);

        vc.addEventHandler(new MyEventHandler());

        vw.getBuffer("grid").addFront(new VisGrid());

        draw();
    }

    public void parameterChanged(ParameterGUI pg, String name)
    {
        if (name.equals("save")) {
            try {
                world.save("/tmp/world.world");
            } catch (IOException ex) {
                System.out.println("ex: "+ex);
            }
        }
    }

    public static void main(String args[])
    {
        SimWorld world = new SimWorld();

        if (args.length > 0) {
            try {
                world = new SimWorld(args[0]);
            } catch (IOException ex) {
                System.out.println("ex: "+ex);
            }
        }

        SimWorldEditor editor = new SimWorldEditor(world);
    }

    void draw()
    {
        VisWorld.Buffer vb = vw.getBuffer("rects");
        for (SimObject obj : world.objects) {
            vb.addBuffered(obj.getVisObject());
        }
        vb.switchBuffer();
    }

    class MyEventHandler extends VisCanvasEventAdapter
    {
        SimBox selectedRect = null;
        double sz = 1;
        Color color = Color.gray;

        public MyEventHandler()
        {
        }

        public String getName()
        {
            return "World Editor";
        }

        public boolean mouseReleased(VisCanvas vc, GRay3D ray, MouseEvent e)
        {
            if (selectedRect == null)
                return false;

            selectedRect = null;

            return false;
        }

        public boolean mouseDragged(VisCanvas vc,  GRay3D ray, MouseEvent e)
        {
            if (selectedRect == null)
                return false;

            int mods = e.getModifiersEx();
            boolean shift = (mods&MouseEvent.SHIFT_DOWN_MASK)>0;
            boolean ctrl = (mods&MouseEvent.CTRL_DOWN_MASK)>0;
            if ((mods & InputEvent.BUTTON1_DOWN_MASK) == 0)
                return false;

            double xy[] = ray.intersectPlaneXY();
            if (ctrl) {
                // resize
                selectedRect.sxyz[0] = Math.max(MIN_SIZE, 2*Math.abs(xy[0] - selectedRect.xyz[0]));
                selectedRect.sxyz[1] = Math.max(MIN_SIZE, 2*Math.abs(xy[1] - selectedRect.xyz[1]));
            } else if (shift) {
                // rotate
                selectedRect.t = Math.atan2(xy[1] - selectedRect.xyz[1], xy[0] - selectedRect.xyz[0]);
            } else {
                // translate
                selectedRect.xyz[0] = xy[0];
                selectedRect.xyz[1] = xy[1];
            }

            draw();

            return true;
        }

        public boolean keyPressed(VisCanvas vc, KeyEvent e)
        {
            if (e.getKeyChar() >= '1' && e.getKeyChar() <= '9') {
                sz = Double.parseDouble(""+e.getKeyChar());

                if (selectedRect != null) {
                    selectedRect.sxyz[2] = sz;
                    selectedRect.xyz[2] = sz / 2;
                    draw();
                }

                return true;
            }

            char c = e.getKeyChar();
            if (c >='a' && c <='z') {
                switch (e.getKeyChar()) {
                    case 'r':
                        color = Color.red; break;
                    case 'g':
                        color = Color.gray; break;
                    case 'b':
                        color = Color.blue; break;
                    case 'm':
                        color = Color.magenta; break;
                    case 'c':
                        color = Color.cyan; break;
                }

                if (selectedRect != null) {
                    selectedRect.color = color;
                    draw();
                }

                return true;
            }

            if (selectedRect != null && (e.getKeyCode()==KeyEvent.VK_DELETE || e.getKeyCode()==KeyEvent.VK_BACK_SPACE)) {
                world.objects.remove(selectedRect);
                selectedRect = null;
                draw();
                return true;
            }
            return false;
        }

        public boolean mousePressed(VisCanvas vc,  GRay3D ray, MouseEvent e)
        {
            int mods = e.getModifiersEx();
            boolean shift = (mods&MouseEvent.SHIFT_DOWN_MASK)>0;
            boolean ctrl = (mods&MouseEvent.CTRL_DOWN_MASK)>0;
            if ((mods & InputEvent.BUTTON1_DOWN_MASK) == 0)
                return false;

            double xy[] = ray.intersectPlaneXY();

            if (ctrl) {
                selectedRect = new SimBox();
                selectedRect.xyz = new double[] { xy[0], xy[1], sz / 2};
                selectedRect.sxyz = new double[] { 1, 1, sz };
                selectedRect.t = 0;
                selectedRect.color = color;

                world.objects.add(selectedRect);

            } else {
                for (SimObject obj : world.objects) {
                    if (obj instanceof SimBox) {
                        SimBox r = (SimBox) obj;

                        if (r.collisionSphere(new double[] {xy[0], xy[1], 0}) == 0) {
                            selectedRect = r;
                            break;
                        }
                    }
                }

                if (selectedRect == null)
                    return false;
            }

            draw();

            return true;
        }
    }
}

