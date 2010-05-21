package april.vis;

import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.imageio.*;
import java.util.*;
import java.util.jar.JarFile;

import javax.media.opengl.*;
import javax.media.opengl.glu.*;

import april.util.*;

/** Basic JOGL functionality test. **/
public class JoglTest
{
    JFrame          frame;
    GLAutoDrawable  panel;

    public static final long serialVersionUID=1001;

    static
    {
        JoglLoader.initialize();
    }

    public static void main(String args[])
    {
        JoglTest impl = new JoglTest();
    }

    public JoglTest()
    {
        frame = new JFrame("Jogl Test");
        frame.setLayout(new BorderLayout());

        GLCapabilities caps = new GLCapabilities();
        caps.setHardwareAccelerated(true);
        caps.setDoubleBuffered(true);
        caps.setAlphaBits(8);

        caps.setRedBits(8);
        caps.setBlueBits(8);
        caps.setGreenBits(8);
        caps.setDepthBits(24);

        caps.setSampleBuffers(true);
        caps.setNumSamples(4);

        if (EnvUtil.getProperty("vis.glcanvas", true))
            panel = new GLCanvas(caps);
        else
            panel = new GLJPanel(caps);

        panel.addGLEventListener(new MyGLEventListener());

        frame.add((Component) panel, BorderLayout.CENTER);

        frame.setBackground(Color.blue);
        frame.setSize(600,400);
        frame.setVisible(true);

        System.out.println(panel.getWidth());

        while (true)
        {
            try {
                System.in.read();
            } catch (IOException ex) {
            }

            npoints += 1000;
            panel.repaint();
        }
    }

    int npoints = 1000;

    class MyGLEventListener implements GLEventListener
    {
        public void init(GLAutoDrawable drawable)
        {
            System.out.println("init");
        }

        public void display(GLAutoDrawable drawable)
        {
            System.out.println("display");

            GL gl = drawable.getGL();
            GLU glu = new GLU();

            gl.glEnable(GL.GL_NORMALIZE);
            gl.glEnable(GL.GL_DEPTH_TEST);
            gl.glDepthFunc(GL.GL_LEQUAL);
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

            gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL.GL_FILL);

            gl.glEnable(GL.GL_LINE_STIPPLE);

            gl.glHint(GL.GL_POLYGON_SMOOTH_HINT, GL.GL_NICEST);
            gl.glDisable(GL.GL_POLYGON_SMOOTH);

            gl.glEnable(GL.GL_POINT_SMOOTH);
            gl.glHint(GL.GL_POINT_SMOOTH_HINT, GL.GL_NICEST);

            gl.glEnable(GL.GL_LINE_SMOOTH);
            gl.glHint(GL.GL_LINE_SMOOTH_HINT, GL.GL_NICEST);

            gl.glHint(GL.GL_PERSPECTIVE_CORRECTION_HINT, GL.GL_NICEST);

            Color backgroundColor = Color.black;

            gl.glEnable(GL.GL_MULTISAMPLE);

            /*
              glu.gluLookAt(.5, .5, 5,
              .5, .5, .5,
              1, 0, 0);
            */

            // alpha is 4th channel. 1.0=opaque.
            gl.glClearColor(backgroundColor.getRed()/255f,
                            backgroundColor.getGreen()/255f,
                            backgroundColor.getBlue()/255f,
                            1.0f);

            gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT |
                       GL.GL_ACCUM_BUFFER_BIT | GL.GL_STENCIL_BUFFER_BIT);
            gl.glClearDepth(1.0f);


            Color color = Color.gray;

            gl.glColor4f(color.getRed()/255.0f,
                         color.getGreen()/255.0f,
                         color.getBlue()/255.0f,
                         color.getAlpha()/255.0f);

            gl.glPointSize(1.0f);

            Random r = new Random(0);

            System.out.println("npoints: "+npoints);

            for (int j = 0 ; j < npoints/180; j++)
            {
                gl.glBegin(gl.GL_POINTS);

                for (int i = 0; i < 180; i++)
                    gl.glVertex3f(r.nextFloat()-.5f, r.nextFloat()-.5f, r.nextFloat());

                gl.glEnd();
            }
        }

        public synchronized void reshape(GLAutoDrawable drawable, int i, int x, int width, int height)
        {
            System.out.println("reshape: "+width+" "+height);
        }

        public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged)
        {
            System.out.println("changed");
        }
    }
}
