package april.image.corner;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.imageio.*;

import april.util.*;
import april.image.*;
import april.vis.*;
import april.jmat.*;

public class CornerTest implements ParameterListener
{
    JFrame jf;
    VisWorld vw = new VisWorld();
    VisLayer vl = new VisLayer(vw);
    VisCanvas vc = new VisCanvas(vl);
    ParameterGUI pg = new ParameterGUI();

    BufferedImage im;

    public static void main(String args[])
    {
        try {
            BufferedImage im = ImageIO.read(new File(args[0]));
            FloatImage fim = new FloatImage(im);
            fim = fim.interpolate(1.5, 5).normalize();
            new CornerTest(fim.makeImage());
        } catch (IOException ex) {
            System.out.println("ex: "+ex);
        }
    }

    public CornerTest(BufferedImage im)
    {
        this.im = im;

        jf = new JFrame("CornerTest");
        jf.setLayout(new BorderLayout());

        pg.addIntSlider("halfsize", "window size/2", 0, 10, 2);
        pg.addDoubleSlider("thresh", "strength thresh", 0, 1, 0.01);

        pg.addListener(this);

        jf.add(vc, BorderLayout.CENTER);
        jf.add(pg.getPanel(), BorderLayout.SOUTH);
        jf.setSize(800,600);
        jf.setVisible(true);

        vl.cameraManager.fit2D(new double[] {0, im.getHeight()}, new double[] {im.getWidth(), 0}, true);
        vl.backgroundColor = new Color(128,128,128);
        ((DefaultCameraManager) vl.cameraManager).interfaceMode = 1.5;

        jf.add(new LayerBufferPanel(vc), BorderLayout.EAST);

        update();
    }


    public void parameterChanged(ParameterGUI pg, String name)
    {
        update();
    }

    public void update()
    {
        FloatImage fim = new FloatImage(im);
        KanadeTomasi detector = new KanadeTomasi(pg.gi("halfsize"));

        FloatImage response = detector.computeResponse(fim);
        response = response.normalize();

        BufferedImage out = response.makeColorImage();

        if (true) {
            VisWorld.Buffer vb = vw.getBuffer("original");
            vb.addBack(new VisChain(LinAlg.translate(0, im.getHeight(), 0),
                                    LinAlg.scale(1, -1, 1),
                                    new VzImage(im)));
            vb.swap();
        }

        if (true) {
            VisWorld.Buffer vb = vw.getBuffer("response");
            vb.addBack(new VisChain(LinAlg.translate(0, out.getHeight(), 0),
                                    LinAlg.scale(1, -1, 1),
                                    new VzImage(out)));
            vb.swap();
        }

        if (true) {
            VisWorld.Buffer vb = vw.getBuffer("corners");

            double thresh = pg.gd("thresh");

            ArrayList<float[]> corners = response.localMaxima();
            for (float corner[] : corners) {
                if (corner[2] < thresh)
                    continue;

                vb.addBack(new VisChain(LinAlg.translate(0, out.getHeight(), 0),
                                        LinAlg.scale(1, -1, 1),
                                        LinAlg.translate(corner[0], corner[1], 0),
                                        new VzCircle(4, Color.yellow)));
            }

            vb.swap();
        }
    }
}
