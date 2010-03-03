package april.vis;

import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.nio.channels.*;
import java.nio.*;

import javax.imageio.*;

import javax.media.opengl.*;
import javax.media.opengl.glu.*;
import javax.swing.*;

import com.sun.opengl.util.*;

import april.jmat.geom.*;
import april.jmat.*;
import april.util.*;

public class VisOffscreenCanvas implements VisContext
{
    int       width, height;
    VisWorld  vw;
    Color     backgroundColor = Color.white;

    VisViewManager viewManager;
    VisView   thisView;

    boolean faster = false;
    int aaLevel = 0;

    GLPbuffer pbuffer;

    ArrayList<RenderObject> requests = new ArrayList<RenderObject>();

    public boolean debug = false;

    boolean drawGrid = false;
    boolean drawGround = false;
    boolean drawOrigin = false;

    class RenderObject
    {
        BufferedImage im;
    }

    static
    {
        JoglLoader.initialize();
    }

    public VisOffscreenCanvas(int width, int height, VisWorld vw)
    {
        this.width = width;
        this.height = height;
        this.vw = vw;
        this.viewManager = new VisViewManager(this);

        pbuffer = GLDrawableFactory.getFactory().createGLPbuffer(makeCapabilities(16, true, true, aaLevel),
                                                                 null,
                                                                 width, height,
                                                                 null);

        pbuffer.addGLEventListener(new MyGLEventListener());
    }

    public int getWidth()
    {
        return width;
    }

    public int getHeight()
    {
        return height;
    }

    GLCapabilities makeCapabilities(int depthbits, boolean hwaccel, boolean doublebuffered, int aalevel)
    {
        GLCapabilities caps = new GLCapabilities();
        caps.setAlphaBits(8);
        caps.setRedBits(8);
        caps.setBlueBits(8);
        caps.setGreenBits(8);
        caps.setHardwareAccelerated(hwaccel);
        caps.setDoubleBuffered(doublebuffered);
        caps.setDepthBits(depthbits);
        caps.setSampleBuffers(aalevel > 0);
        if (aalevel > 0)
            caps.setNumSamples(aalevel);

        return caps;
    }

    class MyGLEventListener implements GLEventListener
    {
        public void init(GLAutoDrawable drawable)
        {
            if (debug)
                System.out.println("init");
        }

        public void display(GLAutoDrawable drawable)
        {
            if (debug)
                System.out.println("display");

            if (true) {
                VisWorld.Buffer vb = vw.getBuffer("__VISCANVAS_GROUND");
                vb.setDrawOrder(-10000);
                VisGrid vg = new VisGrid();
                vg.drawGrid = drawGrid;
                vg.drawGround = drawGround;
                vb.addBuffered(new VisDepthTest(false, vg));

                if (drawOrigin)
                    vb.addBuffered(new VisData(new VisDataPointStyle(Color.gray, 4),
                                               new double[3]));
                vb.switchBuffer();
            }

            GL gl = drawable.getGL();
            GLU glu = new GLU();

            int viewport[] = new int[4];

            gl.glGetIntegerv(gl.GL_VIEWPORT, viewport, 0);

            thisView = viewManager.getView(viewport);

            // reminder: alpha is 4th channel. 1.0=opaque.
            Color backgroundColor = getBackground();
            gl.glClearColor(backgroundColor.getRed()/255f,
                            backgroundColor.getGreen()/255f,
                            backgroundColor.getBlue()/255f,
                            1.0f);

            gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT); // |  GL.GL_ACCUM_BUFFER_BIT | GL.GL_STENCIL_BUFFER_BIT);
            gl.glClearDepth(1.0f);

            gl.glEnable(GL.GL_NORMALIZE);

            if (true) {
                gl.glLightModeli(GL.GL_LIGHT_MODEL_TWO_SIDE, GL.GL_TRUE);
                gl.glLightfv(GL.GL_LIGHT0, GL.GL_AMBIENT, new float[] {.4f, .4f, .4f, 1.0f}, 0);
                gl.glLightfv(GL.GL_LIGHT0, GL.GL_DIFFUSE, new float[] {.8f, .8f, .8f, 1.0f}, 0);
                gl.glLightfv(GL.GL_LIGHT0, GL.GL_SPECULAR, new float[] {.5f, .5f, .5f, 1.0f}, 0);
                gl.glLightfv(GL.GL_LIGHT0, GL.GL_POSITION, new float[] {100f, 150f, 120f, 1}, 0);

                gl.glEnable(GL.GL_LIGHTING);
                gl.glEnable(GL.GL_LIGHT0);
                gl.glEnable(GL.GL_COLOR_MATERIAL);
                gl.glColorMaterial(GL.GL_FRONT_AND_BACK, GL.GL_AMBIENT_AND_DIFFUSE);
            }

            if (true) {
                gl.glLightfv(GL.GL_LIGHT1, GL.GL_AMBIENT, new float[] {.1f, .1f, .1f, 1.0f}, 0);
                gl.glLightfv(GL.GL_LIGHT1, GL.GL_DIFFUSE, new float[] {.1f, .1f, .1f, 1.0f}, 0);
                gl.glLightfv(GL.GL_LIGHT1, GL.GL_SPECULAR, new float[] {.5f, .5f, .5f, 1.0f}, 0);
                gl.glLightfv(GL.GL_LIGHT1, GL.GL_POSITION, new float[] {-100f, -150f, 120f, 1}, 0);

                gl.glEnable(GL.GL_LIGHTING);
                gl.glEnable(GL.GL_LIGHT1);
                gl.glEnable(GL.GL_COLOR_MATERIAL);
            }

            gl.glDepthFunc(GL.GL_LEQUAL);
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
            gl.glEnable(GL.GL_DEPTH_TEST);

            gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL.GL_FILL);

            gl.glDisable(GL.GL_LINE_STIPPLE);

            gl.glShadeModel(GL.GL_SMOOTH);

            if (faster) {
                gl.glDisable(GL.GL_POINT_SMOOTH);
                gl.glDisable(GL.GL_LINE_SMOOTH);
            } else {
                gl.glEnable(GL.GL_POINT_SMOOTH);
                gl.glHint(GL.GL_POINT_SMOOTH_HINT, GL.GL_NICEST);

                gl.glEnable(GL.GL_LINE_SMOOTH);
                gl.glHint(GL.GL_LINE_SMOOTH_HINT, GL.GL_NICEST);

                //		gl.glLineStipple(1, (short) 0xffff);
                //		gl.glEnable(GL.GL_LINE_STIPPLE);
            }

            /////////// PROJECTION MATRIX ////////////////
            gl.glMatrixMode(gl.GL_PROJECTION);
            gl.glLoadIdentity();
            gl.glMultMatrixd(thisView.projectionMatrix.getColumnPackedCopy(), 0);

            /////////// MODEL MATRIX ////////////////
            gl.glMatrixMode(gl.GL_MODELVIEW);
            gl.glLoadIdentity();
            gl.glMultMatrixd(thisView.modelMatrix.getColumnPackedCopy(), 0);

            //	    gl.glGetDoublev(gl.GL_MODELVIEW_MATRIX, model_matrix, 0);
            //	    gl.glGetDoublev(gl.GL_PROJECTION_MATRIX, proj_matrix, 0);

            if (aaLevel > 0)
                gl.glEnable(GL.GL_MULTISAMPLE);


            //////// render
            vw.render(VisOffscreenCanvas.this, gl, glu);

            BufferedImage im = new BufferedImage(width, height,
                                                 BufferedImage.TYPE_3BYTE_BGR);

            byte imdata[] = ((DataBufferByte) im.getRaster().getDataBuffer()).getData();

            // read the BGR values into the image buffer
            gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
            gl.glReadPixels(0, 0, width, height, GL.GL_BGR,
                            GL.GL_UNSIGNED_BYTE, ByteBuffer.wrap(imdata));

            flipImage(width*3, height, imdata);

            synchronized (requests) {
                for (RenderObject r : requests) {
                    synchronized(r) {
                        r.im = im;
                        r.notifyAll();
                    }
                }
            }
        }

        // vertically flip image
        void flipImage(int stride, int height, byte b[])
        {
            byte tmp[] = new byte[stride];

            for (int row = 0; row < (height-1)/2; row++) {

                int rowa = row;
                int rowb = height-1 - rowa;

                // swap rowa and rowb

                // tmp <-- rowa
                System.arraycopy(b, rowa*stride, tmp, 0, stride);

                // rowa <-- rowb
                System.arraycopy(b, rowb*stride, b, rowa*stride, stride);

                // rowb <-- tmp
                System.arraycopy(tmp, 0, b, rowb*stride, stride);
            }
        }

        public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height)
        {
            if (debug)
                System.out.println("reshape");
        }

        public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged)
        {
            if (debug)
                System.out.println("changed");
        }
    }

    public VisWorld getWorld()
    {
        return vw;
    }

    public VisViewManager getViewManager()
    {
        return viewManager;
    }

    /** No-op: we only render frames when explicitly requested by getFrame() **/
    public void draw()
    {
        // don't do anything.
    }

    public VisView getRenderingView()
    {
        return thisView;
    }

    public java.awt.Color getBackground()
    {
        return backgroundColor;
    }

    public BufferedImage getImage()
    {
        BufferedImage im = null;

        RenderObject r = new RenderObject();
        synchronized (requests) {
            requests.add(r);
        }

        pbuffer.repaint();

        synchronized (r) {
            if (r.im == null) {
                try {
                    r.wait();
                } catch (InterruptedException ex) {
                }
            }
        }

        synchronized (requests) {
            requests.remove(r);
        }

        return r.im;
    }

    public static void main(String args[])
    {
        VisWorld vw = new VisWorld();
        VisOffscreenCanvas vc = new VisOffscreenCanvas(300, 200, vw);

        vc.getViewManager().lookAt(new double[] {0, 0, 4},
                                   new double[] {0, 0, 0},
                                   new double[] { 0, 1, 0 });

        VisWorld.Buffer vb = vw.getBuffer("foo");
        vb.addBuffered(new VisBox(0, 0, 0, 1, 1, 1, Color.blue));
        vb.switchBuffer();

        JFrame jf = new JFrame("VisOffscreenCanvas Test");
        jf.setLayout(new BorderLayout());
        JImage jim = new JImage(vc.getImage());
        jf.add(jim, BorderLayout.CENTER);
        jf.setSize(600,400);
        jf.setVisible(true);
    }
}
