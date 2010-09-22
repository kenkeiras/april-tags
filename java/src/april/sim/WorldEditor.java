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

public class WorldEditor implements ParameterListener, VisConsole.Listener
{
    JFrame jf;
    VisWorld vw = new VisWorld();
    VisCanvas vc = new VisCanvas(vw);
    VisConsole console = new VisConsole(vc, vw);

    SimWorld world;

    static final double MIN_SIZE = 0.25;

    ParameterGUI pg = new ParameterGUI();

    public WorldEditor(SimWorld world)
    {
        this.world = world;

        pg.addButtons("save", "save");
        pg.addListener(this);

        jf = new JFrame("WorldEditor");
        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);
        jf.add(pg, BorderLayout.SOUTH);

        jf.setSize(800,600);
        jf.setVisible(true);

        vc.addEventHandler(new MyEventHandler());

        vw.getBuffer("grid").addFront(new VisGrid());

        console.addListener(this);
        draw();
    }

    public boolean consoleCommand(VisConsole vc, PrintStream out, String command)
    {
        String toks[] = command.trim().split("\\s+");
        if (toks.length==0)
            return false;

        if (toks[0].equals("save")) {
            String path = "/tmp/myworld.world";
            if (toks.length > 1)
                path = toks[1];
            try {
                world.write(path);
            } catch (IOException ex) {
                out.println("ex: "+ex);
            }
            return true;
        }

        out.printf("Unknown command");
        return false;
    }

    public ArrayList<String> consoleCompletions(VisConsole vc, String prefix)
    {
        String cs[] = new String[] { "save", "load" };

        ArrayList<String> as = new ArrayList<String>();
        for (String s: cs)
            as.add(s);

        return as;
    }

    public void parameterChanged(ParameterGUI pg, String name)
    {
        if (name.equals("save")) {
            try {
                world.write("/tmp/world.world");
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

        WorldEditor editor = new WorldEditor(world);
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
        SimObject selectedObject = null;

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
            if (selectedObject == null)
                return false;

            selectedObject = null;

            return false;
        }

        public boolean mouseDragged(VisCanvas vc,  GRay3D ray, MouseEvent e)
        {
            if (selectedObject == null)
                return false;

            int mods = e.getModifiersEx();
            boolean shift = (mods&MouseEvent.SHIFT_DOWN_MASK)>0;
            boolean ctrl = (mods&MouseEvent.CTRL_DOWN_MASK)>0;
            if ((mods & InputEvent.BUTTON1_DOWN_MASK) == 0)
                return false;

            double xy[] = ray.intersectPlaneXY();
            if (ctrl) {
                if (selectedObject instanceof SimBox) {
                    // resize
                    SimBox sb = (SimBox) selectedObject;
                    double T[][] = selectedObject.getPose();
                    sb.sxyz[0] = Math.max(MIN_SIZE, 2*Math.abs(xy[0] - T[0][3]));
                    sb.sxyz[1] = Math.max(MIN_SIZE, 2*Math.abs(xy[1] - T[1][3]));
                }
            } else if (shift) {
                // rotate
                double T[][] = selectedObject.getPose();
                double t = Math.atan2(xy[1] - T[1][3], xy[0] - T[0][3]);
                double R[][] = LinAlg.rotateZ(t);
                for (int i = 0; i < 3; i++)
                    for (int j = 0; j < 3; j++)
                        T[i][j] = R[i][j];
                selectedObject.setPose(T);
            } else {
                // translate
                double T[][] = selectedObject.getPose();
                T[0][3] = xy[0];
                T[1][3] = xy[1];
                selectedObject.setPose(T);
            }

            draw();

            return true;
        }

        public boolean keyPressed(VisCanvas vc, KeyEvent e)
        {
            if (e.getKeyChar() >= '1' && e.getKeyChar() <= '9') {
                sz = Double.parseDouble(""+e.getKeyChar());

                if (selectedObject != null && selectedObject instanceof SimBox) {
                    SimBox sb = (SimBox) selectedObject;
                    sb.sxyz[2] = sz;
                    sb.T[2][3] = sz / 2;
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

                if (selectedObject != null && selectedObject instanceof SimBox) {
                    ((SimBox) selectedObject).color = color;
                    draw();
                }

                return true;
            }

            if (selectedObject != null && (e.getKeyCode()==KeyEvent.VK_DELETE || e.getKeyCode()==KeyEvent.VK_BACK_SPACE)) {
                world.objects.remove(selectedObject);
                selectedObject = null;
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
                selectedObject = new SimBox();
                double T[][] = LinAlg.identity(4);
                T[0][3] = xy[0];
                T[1][3] = xy[1];
                T[2][3] = sz/2;
                selectedObject.setPose(T);

                ((SimBox) selectedObject).sxyz = new double[] { 1, 1, sz };
                ((SimBox) selectedObject).color = color;

                world.objects.add(selectedObject);

            } else {
                for (SimObject obj : world.objects) {
                    if (obj instanceof SimBox) {
                        SimBox r = (SimBox) obj;

                        if (Collisions.collision(r.getShape(), new CompoundShape(LinAlg.translate(xy[0], xy[1], 0),
                                                                                 new SphereShape(0.1)))) {
                            selectedObject = r;
                            break;
                        }
                    }
                }

                if (selectedObject == null)
                    return false;
            }

            draw();

            return true;
        }
    }
}

