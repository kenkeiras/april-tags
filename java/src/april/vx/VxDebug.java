package april.vx;

import java.util.*;
import java.io.*;
import javax.swing.*;
import java.awt.image.*;
import javax.imageio.*;

import april.jmat.*;
import april.util.*;

public class VxDebug
{


    static int strlen(byte vals[])
    {
        for (int i = 0; ; i++) {
            if (vals[i] == 0)
                return i+1;
        }
    }

    // arg[0] is path to april/java/shaders/ directory
    public static void main(String args[]) throws IOException
    {
        // BufferedImage img = ImageUtil.convertImage(ImageIO.read(new File(args[0])), BufferedImage.TYPE_3BYTE_BGR);
        // VxTexture vtex = new VxTexture(img);

        int width = 480, height = 480;
        VxLocalServer vxls = new VxLocalServer(width,height);
        double proj_d[][] = LinAlg.matrixAB(VxUtil.gluPerspective(60.0f, width*1.0f/height, 0.1f, 5000.0f),
                                            VxUtil.lookAt(new double[]{0,0,10}, new double[3], new double[]{0,1,0}));

        float proj[][] = VxUtil.copyFloats(proj_d);
        vxls.set_system_pm_matrix(proj);
        VxWorld vw = new VxWorld(vxls);

        ArrayList<VxObject> progs1 = new ArrayList();
        if (true) {
            VxProgram vp = VxProgram.make("multi-colored");//new VxProgram(vertRx,fragRx);
            vp.setVertexAttrib("position", new VxVertexAttrib(new float[]{1.0f, 1.0f,
                                                                          0.0f, 1.0f,
                                                                          0.0f, 0.0f,
                                                                          1.0f, 0.0f},
                    2));

            vp.setVertexAttrib("color", new VxVertexAttrib(new float[] { 1.0f, 0.0f, 0.0f,
                                                                         1.0f, 0.0f, 1.0f,
                                                                         0.0f, 1.0f, 0.0f,
                                                                         1.0f, 1.0f, 0.0f},
                    3));

            vp.setElementArray(new VxIndexData(new int[]{0,1,2,
                                                         2,3,0}), Vx.GL_TRIANGLES);

            progs1.add(vp);
        }

        if (true) {
            ArrayList<double[]> pts = new ArrayList();

            Random r = new Random(99);
            for (int i = 0; i < 100; i++)
                pts.add(new double[]{10 - 20*r.nextDouble(),
                                     10 - 20*r.nextDouble(),
                                     10 - 20*r.nextDouble()});

            VxVertexAttrib points = new VxVertexAttrib(pts);
            VxPoints vpts = new VxPoints(points, java.awt.Color.red);

            // progs2.add(vpts);

            // XXX This breaks totally
            vw.getBuffer("points").stage(vpts);
            vw.getBuffer("points").commit();
        }

        JFrame jf = new JFrame();

        JImage jim = new JImage();
        jim.setFlipY(true);
        jf.add(jim);
        jf.setSize(width,height+22);
        jf.setVisible(true);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);


        // Render loop
        for (int i = 0; i < 24; i++) {

            int type = i % 3;
            switch(type) {
                case 0:
                case 1:
                    for (VxObject vp : progs1)
                        vw.getBuffer("first-buffer").stage(vp);
                    break;
                case 3:
                    break;
            }
            vw.getBuffer("first-buffer").commit();


            System.out.printf("Render %d:\n",i);
            vxls.render(width,height);

            BufferedImage canvas = new BufferedImage(width,height, BufferedImage.TYPE_3BYTE_BGR);
            byte buf[] = ((DataBufferByte) (canvas.getRaster().getDataBuffer())).getData();
            vxls.read_pixels(width,height,buf);



            jim.setImage(canvas);

            System.out.printf("Render finished %d:\n",i);

            TimeUtil.sleep(500);

        }
    }
}