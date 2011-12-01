package april.vis.examples;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

import april.jmat.*;
import april.vis.*;

import javax.imageio.*;

public class Zoo
{
    public static void main(String args[])
    {
        JFrame jf = new JFrame("Vis Zoo");
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setSize(600,400);
        jf.setLayout(new BorderLayout());

        VisWorld vw = new VisWorld();
        VisLayer vl = new VisLayer(vw);
        VisCanvas vc = new VisCanvas(vl);

        VzGrid.addGrid(vw, new VzGrid(new VzMesh.Style(new Color(80, 80, 80, 100)),
                                      new VzLines.Style(new Color(40, 40, 40), 1)));


        VzLines.Style lineStyle = new VzLines.Style(Color.blue, 3);
        VzMesh.Style meshStyle = new VzMesh.Style(Color.red);

        BufferedImage im = null;
        try {
            im = ImageIO.read(new File("/home/ebolson/earth.png"));
        } catch (IOException ex) {
            System.out.println("ex: "+ex);
        }

        VisObject objects[] = new VisObject[] { new VzAxes(),
                                                new VzBox(lineStyle, meshStyle),
                                                new VzCamera(),
                                                new VzCircle(lineStyle, meshStyle),
                                                new VzCone(meshStyle),
                                                new VzCylinder(meshStyle),
                                                new VzRobot(lineStyle, meshStyle),
                                                new VzSphere(meshStyle),
                                                //xxx Texture file missing new VzSphere(new VisTexture(im)),
                                                new VzSquarePyramid(lineStyle, meshStyle),
                                                new VzStar(lineStyle, meshStyle),
                                                new VzText(VzText.ANCHOR.CENTER, "<<sansserif-10,scale=.1,dropshadow=false>>Hi!"),
                                                new VzSquare(lineStyle, meshStyle),
                                                new VzTriangle(lineStyle, meshStyle),
        };

        VisWorld.Buffer vb = vw.getBuffer("zoo");
        int cols = (int) (Math.sqrt(objects.length) + 1);
        int rows = objects.length / cols + 1;
        int grid = 10;

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int idx = y*cols + x;
                if (idx >= objects.length)
                    break;

                VisObject vo = objects[idx];

                vb.addBack(new VisChain(LinAlg.translate(x*grid + grid/2, rows*grid - (y*grid + grid/2), 0),
                                        new VisChain(LinAlg.translate(0,0,0.1),
                                                     new VzSquare(grid, grid, new VzLines.Style(Color.gray, 2))),
                                        vo,
                                        LinAlg.translate(0, grid*.4, 0),
                                        LinAlg.scale(.02, .02, .02),
                                        new VzText(VzText.ANCHOR.CENTER, "<<sansserif-24,dropshadow=false>>"+vo.getClass().getName())));
            }
        }

        vb.swap();

        jf.add(vc);
        jf.setSize(600, 400);
        jf.setVisible(true);

        vl.cameraManager.fit2D(new double[] { 0, 0 }, new double[] { cols*grid, rows*grid }, true);
    }
}
