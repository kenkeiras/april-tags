package april.vx;

import java.util.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.*;

// Class which maintains a VxLocalRenderer instance (preferably through some synchronous wrapper)
// Also can be painted as a component
public class VxCanvas extends JComponent
{

    VxLocalRenderer rend;

    BufferedImage im;

    int targetFrameRate = 5; // draw really slow for now

    public VxCanvas(VxLocalRenderer rend)
    {
        this.rend = rend;
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