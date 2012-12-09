package april.vx;

import java.util.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.*;

import april.jmat.*;

// Class which maintains a VxLocalRenderer instance (preferably through some synchronous wrapper)
// Also can be painted as a component
public class VxCanvas extends JComponent
{

    VxLocalRenderer rend;

    BufferedImage im;

    int targetFrameRate = 5; // draw really slow for now

    HashMap<Integer, VxLayer> layerMap = new HashMap();

    public VxCanvas(VxLocalRenderer rend, VxLayer ... layers)
    {
        this.rend = rend;
        for (VxLayer layer : layers)
            layerMap.put(layer.layerID, layer);

        int canvas_size[] = rend.get_canvas_size();
        int width = canvas_size[0], height = canvas_size[1];


        new RepaintThread().start();
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

                        double PM[][] = LinAlg.matrixAB(cp.getProjectionMatrix(), cp.getModelViewMatrix());
                        rend.set_layer_pm_matrix(layer.layerID,
                                                 VxUtil.copyFloats(PM));
                    }

                    rend.render(width,height,buf);

                    im = canvas;
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


}