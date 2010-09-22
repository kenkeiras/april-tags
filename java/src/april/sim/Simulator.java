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

import lcm.util.*;

import javax.media.opengl.*;
import javax.media.opengl.glu.*;

public class Simulator implements VisConsole.Listener
{
    JFrame jf;
    VisWorld vw = new VisWorld();
    VisCanvas vc = new VisCanvas(vw);
    VisConsole console = new VisConsole(vc, vw);

    SimWorld world;

    static final double MIN_SIZE = 0.25;

    String simObjectClass = "april.sim.SimBox";
    SimObject selectedObject = null;
    FindSimObjects finder = new FindSimObjects();

    public Simulator(SimWorld world)
    {
        this.world = world;

        jf = new JFrame("Simulator");
        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);

        jf.setSize(800,600);
        jf.setVisible(true);

        vc.addEventHandler(new MyEventHandler());

        vw.getBuffer("grid").addFront(new VisGrid());

        if (true) {
            VisWorld.Buffer vb = vw.getBuffer("SimWorld");
            vb.addBuffered(new VisSimWorld());
            vb.switchBuffer();
        }

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
                out.printf("Done");
            } catch (IOException ex) {
                out.println("ex: "+ex);
            }
            return true;
        }

        if (toks[0].equals("class")) {
            if (toks.length==2) {
                SimObject sobj = SimWorld.createObject(world, simObjectClass);

                if (sobj != null) {
                    simObjectClass = toks[1];
                    out.printf("Done");
                } else {
                    out.printf("Unknown or invalid class name: "+toks[1]+"\n");
                }
                return true;
            } else {
                out.printf("usage: class <classname>\n");
                return true;
            }
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

        for (String s : finder.classes)
            as.add("class "+s);
        return as;
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

        Simulator editor = new Simulator(world);
    }

    class VisSimWorld implements VisObject
    {
        public void render(VisContext vc, GL gl, GLU glu)
        {
            synchronized(world) {
                for (SimObject obj : world.objects) {
                    VisChain v = new VisChain(obj.getPose(), obj.getVisObject());
                    v.render(vc, gl, glu);
                }
            }
        }
    }

    void draw()
    {
        if (false) {
            VisWorld.Buffer vb = vw.getBuffer("objects");

            synchronized(world) {
                for (SimObject obj : world.objects) {
                    vb.addBuffered(new VisChain(obj.getPose(),
                                                obj.getVisObject()));
                }
            }

            vb.switchBuffer();
        }

        if (true) {
            VisWorld.Buffer vb = vw.getBuffer("collide-info");

            if (selectedObject != null) {
                // does this object now collide with anything else?
                boolean collide = false;

                synchronized(world) {
                    for (SimObject so : world.objects) {
                        if (so != selectedObject && Collisions.collision(so.getShape(), so.getPose(),
                                                                         selectedObject.getShape(), selectedObject.getPose())) {
                            collide = true;
                            break;
                        }
                    }
                }

                if (collide)
                    vb.addBuffered(new VisText(VisText.ANCHOR.BOTTOM_RIGHT, "<<blue>>Collision"));
            }

            vb.switchBuffer();
        }
    }

    class MyEventHandler extends VisCanvasEventAdapter
    {
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
                double T[][] = selectedObject.getPose();

                if (selectedObject instanceof SimBox) {
                    // resize
                    SimBox sb = (SimBox) selectedObject;
                    sb.sxyz[0] = Math.max(MIN_SIZE, 2*Math.abs(xy[0] - T[0][3]));
                    sb.sxyz[1] = Math.max(MIN_SIZE, 2*Math.abs(xy[1] - T[1][3]));
                } else if (selectedObject instanceof SimSphere) {
                    SimSphere s = (SimSphere) selectedObject;

                    s.r = Math.max(MIN_SIZE, Math.sqrt(LinAlg.sq(xy[0] - T[0][3]) +
                                                       LinAlg.sq(xy[1] - T[1][3])));
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
                } else if (selectedObject != null && selectedObject instanceof SimSphere) {
                    ((SimSphere) selectedObject).color = color;
                    draw();
                }


                return true;
            }

            if (selectedObject != null && (e.getKeyCode()==KeyEvent.VK_DELETE || e.getKeyCode()==KeyEvent.VK_BACK_SPACE)) {
                synchronized(world) {
                    world.objects.remove(selectedObject);
                }

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
                // create a new object
                selectedObject = SimWorld.createObject(world, simObjectClass);

                double T[][] = LinAlg.identity(4);
                T[0][3] = xy[0];
                T[1][3] = xy[1];
                T[2][3] = sz/2;
                selectedObject.setPose(T);

                if (selectedObject instanceof SimBox) {
                    ((SimBox) selectedObject).sxyz = new double[] { 1, 1, sz };
                    ((SimBox) selectedObject).color = color;
                }

                synchronized(world) {
                    world.objects.add(selectedObject);
                }

            } else {
                // select an existing object
                double bestd = Double.MAX_VALUE;

                for (SimObject obj : world.objects) {

                    double d = Collisions.collisionDistance(ray.getSource(), ray.getDir(), obj.getShape(), obj.getPose());

                    boolean b = Collisions.collision(obj.getShape(), obj.getPose(),
                                                     new SphereShape(0.1), LinAlg.translate(xy[0], xy[1], 0));

                    if (d < bestd) {
                        selectedObject = obj;
                        bestd = d;
                    }
                }

                if (selectedObject == null)
                    return false;
            }

            draw();

            return true;
        }
    }

    class FindSimObjects implements lcm.util.ClassDiscoverer.ClassVisitor
    {
        ArrayList<String> classes = new ArrayList<String>();

        public FindSimObjects()
        {
            ClassDiscoverer.findClasses(this);
        }

        public void classFound(String jarfile, Class cls)
        {
            boolean good = false;

            for (Class c : cls.getInterfaces()) {
                if (c.equals(SimObject.class))
                    good = true;
            }

            if (good)
                classes.add(cls.getName());
        }
    }
}
