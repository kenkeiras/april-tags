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
        VxLocalRenderer vxlr = new VxLocalRenderer("java://");//width,height);
        double proj_d[][] = LinAlg.matrixAB(VxUtil.gluPerspective(60.0f, width*1.0f/height, 0.1f, 5000.0f),
                                            VxUtil.lookAt(new double[]{0,0,10}, new double[3], new double[]{0,1,0}));

        float proj[][] = VxUtil.copyFloats(proj_d);
        vxlr.set_system_pm_matrix(proj);
        VxWorld vw = new VxWorld(new VxResourceManager(vxlr));

        ArrayList<VxObject> progs1 = new ArrayList();
        ArrayList<VxObject> progs2 = new ArrayList();
        if (true) {

            // VxMesh mesh = new VxMesh(
            //     new VxVertexAttrib(new float[]{1.0f, 1.0f,
            //                                    0.0f, 1.0f,
            //                                    0.0f, 0.0f,
            //                                    1.0f, 0.0f},
            //         2),
            //     new VxIndexData(new int[]{0,1,2,
            //                               2,3,0}),
            //     new VxVertexAttrib(new float[] { 1.0f, 0.0f, 0.0f,
            //                                      1.0f, 0.0f, 1.0f,
            //                                      0.0f, 1.0f, 0.0f,
            //                                      1.0f, 1.0f, 0.0f},
            //         3));

            VxMesh mesh = new VxMesh(
                new VxVertexAttrib(new float[]{1.0f, 1.0f,
                                               0.0f, 1.0f,
                                               0.0f, 0.0f,
                                               1.0f, 0.0f},
                    2),
                new VxIndexData(new int[]{0,1,2,
                                          2,3,0}),
                java.awt.Color.blue);

            progs2.add(mesh);
        }

        if (true) {
            ArrayList<double[]> pts = new ArrayList();

            Random r = new Random(99);
            for (int i = 0; i < 4; i++)
                pts.add(new double[]{10 - 20*r.nextDouble(),
                                     10 - 20*r.nextDouble(),
                                     0.0});

            ArrayList<float[]> cls = new ArrayList();
            for (int i = 0; i < pts.size(); i++)
                cls.add(new float[]{0.0f,1.0f,0f,1f}); //april.vis.ColorUtil.seededColor(i).getRGBComponents(null)); //

            VxVertexAttrib points = new VxVertexAttrib(pts);
            VxVertexAttrib colors = new VxVertexAttrib(cls);


            // VxPoints vpts = new VxPoints(points, java.awt.Color.red);
            VxPoints vpts = new VxPoints(points, colors);

            progs1.add(vpts);

        }

        JFrame jf = new JFrame();

        JImage jim = new JImage();
        jim.setFlipY(true);
        jf.add(jim);
        jf.setSize(width,height+22);
        jf.setVisible(true);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);


        // We statically load the second array, and dynamically switch the first:
        {
            for (VxObject vp : progs2)
                vw.getBuffer("static-buffer").stage(vp);
            vw.getBuffer("static-buffer").commit();
        }

        // Render loop
        for (int i = 0; i < 24; i++) {

            int type = i % 2;
            switch(type) {
                case 0:
                    break;
                case 1:
                    for (VxObject vp : progs1)
                        vw.getBuffer("dynamic-buffer").stage(vp);
                    break;
            }
            vw.getBuffer("dynamic-buffer").commit();


            System.out.printf("Render %d:\n",i);
            BufferedImage canvas = new BufferedImage(width,height, BufferedImage.TYPE_3BYTE_BGR);
            byte buf[] = ((DataBufferByte) (canvas.getRaster().getDataBuffer())).getData();
            vxlr.render(width,height,buf);



            jim.setImage(canvas);

            System.out.printf("Render finished %d:\n",i);

            TimeUtil.sleep(500);

        }
    }
}