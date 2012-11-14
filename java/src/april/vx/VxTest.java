package april.vx;

import java.util.*;
import java.io.*;
import javax.swing.*;
import java.awt.image.*;
import javax.imageio.*;

import april.jmat.*;
import april.util.*;

public class VxTest
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
        BufferedImage img = ImageUtil.convertImage(ImageIO.read(new File(args[0])), BufferedImage.TYPE_3BYTE_BGR);
        VxTexture vtex = new VxTexture(img);

        int width = 480, height = 480;
        VxLocalServer vxls = new VxLocalServer(width,height);
        VxWorld vw = new VxWorld(vxls);

        ArrayList<VxVertexAttrib> point_attribs = new ArrayList();
        {


            // bottom right
            point_attribs.add(new VxVertexAttrib(new float[]{1.0f, 1.0f,
                                                             0.0f, 1.0f,
                                                             0.0f, 0.0f,
                                                             1.0f, 0.0f},
                                                 2));

            // bottom left
            point_attribs.add(new VxVertexAttrib(new float[]{-1.0f, 0.0f,
                                                             0.0f, 0.0f,
                                                             0.0f, 1.0f,
                                                             -1.0f, 1.0f},
                                                 2));


            // top left
            point_attribs.add(new VxVertexAttrib(new float[]{0.0f, 0.0f,
                                                             -1.0f, 0.0f,
                                                             -1.0f, -1.0f,
                                                             0.0f, -1.0f},
                                                 2));



            // top right
            point_attribs.add(new VxVertexAttrib(new float[]{ 0.0f, -1.0f,
                                                              1.0f, -1.0f,
                                                              1.0f, 0.0f,
                                                              0.0f, 0.0f},
                                                 2));

        }

        ArrayList<VxVertexAttrib> color_attribs = new ArrayList();
        {

            color_attribs.add(new VxVertexAttrib(new float[] { 1.0f, 0.0f, 0.0f,
                                                               1.0f, 0.0f, 1.0f,
                                                               0.0f, 1.0f, 0.0f,
                                                               1.0f, 1.0f, 0.0f},
                                                 3));

            color_attribs.add(new VxVertexAttrib(new float []{0.0f, .3f, 1.0f,
                                                              0.5f, 0.3f, 1.0f,
                                                              0.0f, 1.0f, 1.0f,
                                                              0.0f, 1.0f, 1.0f},
                                                 3));

            color_attribs.add(new VxVertexAttrib(new float []{0.0f, 1.0f, 1.0f,
                                                              0.5f, 0.3f, 1.0f,
                                                              0.0f, 1.0f, 1.0f,
                                                              1.0f, 1.0f, 1.0f},
                                                 3));


            color_attribs.add(new VxVertexAttrib(new float []{0.0f, 1.0f, 0.7f,
                                                              0.0f, 1.0f, 1.0f,
                                                              1.0f, .1f, .1f,
                                                              0.5f, 0.3f, 1.0f},
                                                 3));
        }


        VxIndexData index = new VxIndexData(new int[]{0,1,2,
                                                      2,3,0});
        double proj_d[][] = LinAlg.matrixAB(VxUtil.gluPerspective(60.0f, width*1.0f/height, 0.1f, 5000.0f),
                                            VxUtil.lookAt(new double[]{0,0,10}, new double[3], new double[]{0,1,0}));

        float proj[][] = VxUtil.copyFloats(proj_d);

        ArrayList<VxProgram> progs1 = new ArrayList();
        for (int i = 0; i < 4; i+=2) {


            VxProgram vp = VxProgram.make("colored-tri");//new VxProgram(vertRx,fragRx);
            vp.setVertexAttrib("position", point_attribs.get(i));

            vp.setVertexAttrib("color", color_attribs.get(i));

            vp.setUniform("proj", proj);

            vp.setElementArray(index, Vx.GL_TRIANGLES);

            progs1.add(vp);
        }

        ArrayList<VxProgram> progs2 = new ArrayList();
        for (int i = 1; i < 3; i+=2) { // XXX Only render 1
            VxProgram vp = VxProgram.make("colored-tri");
            vp.setVertexAttrib("position", point_attribs.get(i));

            vp.setVertexAttrib("color", color_attribs.get(i));

            vp.setUniform("proj", proj);

            vp.setElementArray(index, Vx.GL_TRIANGLES);

            progs2.add(vp);
        }

        // Now do Texture:
        {
            VxProgram vp = VxProgram.make("tex");
            vp.setVertexAttrib("position", point_attribs.get(3));
            vp.setTexture("tex", vtex);

            vp.setUniform("proj", proj);

            vp.setElementArray(index, Vx.GL_TRIANGLES);

            progs2.add(vp);

        }

        JFrame jf = new JFrame();

        JImage jim = new JImage();
        jf.add(jim);
        jf.setSize(width,height+22);
        jf.setVisible(true);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);


        // Render loop
        for (int i = 0; i < 12; i++) {

            int type = i % 3;
            switch(type) {
                case 0:
                    for (VxProgram vp : progs1)
                        vw.getBuffer("first-buffer").stage(vp);
                    break;
                case 1:
                    for (VxProgram vp : progs2)
                        vw.getBuffer("first-buffer").stage(vp);
                    break;
            }
            vw.getBuffer("first-buffer").commit();

            vxls.render(width,height);

            BufferedImage canvas = new BufferedImage(width,height, BufferedImage.TYPE_3BYTE_BGR);
            byte buf[] = ((DataBufferByte) (canvas.getRaster().getDataBuffer())).getData();
            vxls.read_pixels(width,height,buf);

            jim.setImage(canvas);
            System.out.printf("Render %d \n",i);

            TimeUtil.sleep(1000);

        }
    }
}