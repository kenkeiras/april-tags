package april.vx;

import java.util.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.*;

import java.awt.event.*;

import april.jmat.*;
import april.jmat.geom.*;
// Class which maintains a VxLocalRenderer instance (preferably through some synchronous wrapper)
// Also can be painted as a component
public class VxCanvas extends JComponent
{
    EventHandler eh = new EventHandler();

    VxLocalRenderer rend;

    BufferedImage im;
    RenderInfo lastRenderInfo;

    int targetFrameRate = 5; // draw really slow for now

    HashMap<Integer, VxLayer> layerMap = new HashMap();



    public VxCanvas(VxLocalRenderer rend, VxLayer ... layers)
    {
        this.rend = rend;
        for (VxLayer layer : layers)
            layerMap.put(layer.layerID, layer);

        int canvas_size[] = rend.get_canvas_size();
        int width = canvas_size[0], height = canvas_size[1];


        addMouseMotionListener(eh);
        addMouseListener(eh);
        addMouseWheelListener(eh);
        addKeyListener(eh);

        setFocusTraversalKeysEnabled(false); // Black magic


        new RepaintThread().start();
    }

    public static class RenderInfo
    {
        // The layers, in the order that they were rendered.
        public ArrayList<VxLayer> layers = new ArrayList(); // XXX Need to sort these by order?

        // The position of the layers when they were rendered.
        public HashMap<VxLayer, int[]> layerPositions = new HashMap();

        public HashMap<VxLayer, VxCameraManager.CameraPosition> cameraPositions = new HashMap();
    }

    class RepaintThread extends Thread
    {
        public RepaintThread()
        {
            setDaemon(true);
        }

        public void run()
        {
            while (true) {
                try {
                    Thread.sleep(1000 / targetFrameRate);
                } catch (InterruptedException ex) {
                    System.out.println("ex: "+ex);
                }

                if (VxCanvas.this.isVisible()) {

                    RenderInfo rinfo = new RenderInfo();

                    int width = VxCanvas.this.getWidth();
                    int height = VxCanvas.this.getHeight();

                    BufferedImage canvas = new BufferedImage(width,height, BufferedImage.TYPE_3BYTE_BGR);

                    byte buf[] = ((DataBufferByte) (canvas.getRaster().getDataBuffer())).getData();

                    long mtime = System.currentTimeMillis();

                    // Poll each VxCanvas for camera info XXX nulls for viewports
                    // XXX what to do about layers which associated directly with the renderer (e.g. remotely)

                    // XXX camera management hack
                    // double proj_d[][] = LinAlg.matrixAB(VxUtil.gluPerspective(60.0f, width*1.0f/height, 0.1f, 5000.0f),
                    //                                     VxUtil.lookAt(new double[]{0,0,10}, new double[3], new double[]{0,1,0}));

                    // float proj[][] = VxUtil.copyFloats(proj_d);
                    // ((VxLocalRenderer)rend).set_layer_pm_matrix(1, proj); // XXX LayerID

                    for (VxLayer layer : layerMap.values()) {
                        int layerViewport[] = layer.getAbsoluteViewport(width,height);
                        VxCameraManager.CameraPosition cp = layer.cameraManager.getCameraPosition(layerViewport, mtime);
                        rinfo.layerPositions.put(layer, layerViewport);
                        rinfo.cameraPositions.put(layer, cp);
                        rinfo.layers.add(layer);
                        double PM[][] = LinAlg.matrixAB(cp.getProjectionMatrix(), cp.getModelViewMatrix());
                        rend.set_layer_pm_matrix(layer.layerID,
                                                 VxUtil.copyFloats(PM));
                    }

                    rend.render(width,height,buf, Vx.GL_BGR);

                    im = canvas;
                    lastRenderInfo = rinfo;
                    repaint();
                }
            }
        }
    }


    // implement draw methods, frame rate, camera controls, etc
    public void paintComponent(Graphics _g)
    {
        Graphics2D g = (Graphics2D) _g;

        if (im != null) {
            g.translate(0, getHeight());
            g.scale(1, -1);
            g.drawImage(im, 0, 0, null);
        }
    }


    class EventHandler implements MouseMotionListener, MouseListener, MouseWheelListener, KeyListener
    {
        VxLayer mousePressedLayer;
        VxLayer keyboardFocusLayer;

        int lastex = -1, lastey = -1;

        public void keyPressed(KeyEvent e)
        {
            dispatchKeyEvent(e);
        }

        public void keyReleased(KeyEvent e)
        {
            dispatchKeyEvent(e);
        }

        public void keyTyped(KeyEvent e)
        {
            dispatchKeyEvent(e);
        }

        public void mouseWheelMoved(MouseWheelEvent e)
        {
            dispatchMouseEvent(e);
        }

        public void mouseDragged(MouseEvent e)
        {
            dispatchMouseEvent(e);
        }

        public void mouseMoved(MouseEvent e)
        {
            dispatchMouseEvent(e);
        }

        public void mousePressed(MouseEvent e)
        {
            dispatchMouseEvent(e);
        }

        public void mouseReleased(MouseEvent e)
        {
            dispatchMouseEvent(e);
        }

        public void mouseClicked(MouseEvent e)
        {
            dispatchMouseEvent(e);
        }

        public void mouseEntered(MouseEvent e)
        {
            dispatchMouseEvent(e);
            requestFocus();
        }

        public void mouseExited(MouseEvent e)
        {
            dispatchMouseEvent(e);
        }

        // Find a layer that can consume this event.
        void dispatchMouseEvent(MouseEvent e)
        {
            RenderInfo rinfo = lastRenderInfo;
            if (rinfo == null)
                return;

            int ex = e.getX();
            int ey = getHeight() - e.getY();

            lastex = ex;
            lastey = ey;

            // these events go to the layer that got the MOUSE_PRESSED
            // event, not the layer under the event.
            if (e.getID() == MouseEvent.MOUSE_DRAGGED || e.getID() == MouseEvent.MOUSE_RELEASED) {
                if (mousePressedLayer != null && rinfo.cameraPositions.get(mousePressedLayer) != null)
                    dispatchMouseEventToLayer(VxCanvas.this, mousePressedLayer, rinfo,
                                              rinfo.cameraPositions.get(mousePressedLayer).computeRay(ex, ey), e);

                return;
            }

            for (int lidx = rinfo.layers.size()-1; lidx >= 0; lidx--) {
                VxLayer layer = rinfo.layers.get(lidx);
                if (!layer.enabled)
                    continue;

                int pos[] = rinfo.layerPositions.get(layer);

                GRay3D ray = rinfo.cameraPositions.get(layer).computeRay(ex, ey);

                if (ex >= pos[0] && ey >= pos[1] &&
                    ex < pos[0]+pos[2] && ey < pos[1]+pos[3]) {

                    boolean handled = dispatchMouseEventToLayer(VxCanvas.this, layer, rinfo, ray, e);

                    if (e.getID() == MouseEvent.MOUSE_PRESSED) {
                        if (handled)
                            mousePressedLayer = layer;
                        else
                            mousePressedLayer = null;
                    }

                    if (handled)
                        return;
                }
            }
        }

        // this is used by dispatchMouseEvent. It processes the event
        // handlers within the layer, returning true if one of them
        // consumed the event.
        boolean dispatchMouseEventToLayer(VxCanvas vc, VxLayer layer, VxCanvas.RenderInfo rinfo, GRay3D ray, MouseEvent e)
        {
            boolean handled = false;

            synchronized (layer.eventHandlers) {
                for (VxEventHandler eh : layer.eventHandlers) {

                    switch (e.getID()) {
                        case MouseEvent.MOUSE_PRESSED:
                            mousePressedLayer = layer;
                            handled = eh.mousePressed(VxCanvas.this, layer, rinfo, ray, e);
                            break;
                        case MouseEvent.MOUSE_RELEASED:
                            handled = eh.mouseReleased(VxCanvas.this, layer, rinfo, ray, e);
                            break;
                        case MouseEvent.MOUSE_CLICKED:
                            handled = eh.mouseClicked(VxCanvas.this, layer, rinfo, ray, e);
                            break;
                        case MouseEvent.MOUSE_DRAGGED:
                            handled = eh.mouseDragged(VxCanvas.this, layer, rinfo, ray, e);
                            break;
                        case MouseEvent.MOUSE_MOVED:
                            handled = eh.mouseMoved(VxCanvas.this, layer, rinfo, ray, e);
                            break;
                        case MouseEvent.MOUSE_WHEEL:
                            handled = eh.mouseWheel(VxCanvas.this, layer, rinfo, ray, (MouseWheelEvent) e);
                            break;
                        case MouseEvent.MOUSE_ENTERED:
                            handled = false;
                            break;
                        case MouseEvent.MOUSE_EXITED:
                            handled = false;
                            break;
                        default:
                            System.out.println("Unhandled mouse event id: "+e.getID());
                            handled = false;
                            break;
                    }

                    if (handled)
                        break;
                }
            }

            return handled;
        }

        void dispatchKeyEvent(KeyEvent e)
        {
            RenderInfo rinfo = lastRenderInfo;
            if (rinfo == null)
                return;

            for (int lidx = rinfo.layers.size()-1; lidx >= 0; lidx--) {
                VxLayer layer = rinfo.layers.get(lidx);
                if (!layer.enabled)
                    continue;

                int pos[] = rinfo.layerPositions.get(layer);

                if (lastex >= pos[0] && lastey >= pos[1] &&
                    lastex < pos[0]+pos[2] && lastey < pos[1]+pos[3]) {

                    boolean handled = dispatchKeyEventToLayer(VxCanvas.this, layer, rinfo, e);

                    if (handled)
                        return;
                }
            }
        }

        boolean dispatchKeyEventToLayer(VxCanvas vc, VxLayer layer, VxCanvas.RenderInfo rinfo, KeyEvent e)
        {
            boolean handled = false;

            synchronized (layer.eventHandlers) {
                for (VxEventHandler eh : layer.eventHandlers) {

                    switch (e.getID()) {
                        case KeyEvent.KEY_TYPED:
                            handled = eh.keyTyped(VxCanvas.this, layer, rinfo, e);
                            break;
                        case KeyEvent.KEY_PRESSED:
                            handled = eh.keyPressed(VxCanvas.this, layer, rinfo, e);
                            break;
                        case KeyEvent.KEY_RELEASED:
                            handled = eh.keyReleased(VxCanvas.this, layer, rinfo, e);
                            break;
                        default:
                            System.out.println("Unhandled key event id: "+e.getID());
                            break;
                    }

                    if (handled)
                        break;
                }

                return handled;
            }
        }
    }


}