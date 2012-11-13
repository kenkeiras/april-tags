package april.vx;

import java.io.*;
import javax.swing.*;
import april.util.*;
import java.awt.image.*;

public class VxTest
{

    public static byte[] readFileStringZ(String filename) throws IOException
    {
        File file = new File(filename);
        FileInputStream fis = new FileInputStream(file);

        int len = (int)file.length();

        byte fbytes[] = new byte[len+1];
        int rd = fis.read(fbytes);
        assert(rd == len);
        //last index of fbytes is implicitly '\0'

        return fbytes;
    }

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
        int width = 480, height = 480;
        VxLocalServer vxls = new VxLocalServer(width,height);
        VxWorld vw = new VxWorld(vxls);

        byte vertAttr[] = readFileStringZ(args[0]+"/attr.vert");
        byte fragAttr[] = readFileStringZ(args[0]+"/attr.frag");


        VxResource vertRx = new VxResource(Vx.GL_BYTE, vertAttr, vertAttr.length, 1, VxUtil.allocateID());
        VxResource fragRx = new VxResource(Vx.GL_BYTE, fragAttr, fragAttr.length, 1, VxUtil.allocateID());

        System.out.printf("Vertex Shader length %d %d fragment shader length %d %d\n",
                          vertAttr.length, strlen(vertAttr), fragAttr.length, strlen(vertAttr));
        {
            VxProgram vp = new VxProgram(vertRx,fragRx);

            float pts[] = { 1.0f, 1.0f,
                            0.0f, 1.0f,
                            0.0f, 0.0f,
                            1.0f, 0.0f};

            VxVertexAttrib points = new VxVertexAttrib(pts, 2);
            vp.setVertexAttrib("position", points);

            float cls[] = { 1.0f, 0.0f, 0.0f,
                            1.0f, 0.0f, 1.0f,
                            0.0f, 1.0f, 0.0f,
                            1.0f, 1.0f, 0.0f};
            VxVertexAttrib colors = new VxVertexAttrib(cls, 3);
            vp.setVertexAttrib("color", colors);

            float ident[][] = {{1.0f, 0.0f, 0.0f, 0.0f},
                               {0.0f, 1.0f, 0.0f, 0.0f},
                               {0.0f, 0.0f, 1.0f, 0.0f},
                               {0.0f, 0.0f, 0.0f, 1.0f}};

            vp.setUniform("proj", ident);

            int idxs[] = {0,1,2,
                          2,3,0};
            VxIndexData index = new VxIndexData(idxs);
            vp.setElementArray(index, Vx.GL_TRIANGLES);

            vw.getBuffer("first-buffer").stage(vp);
        }

        {
            VxProgram vp = new VxProgram(vertRx, fragRx);

            float pts[] = {0.0f, 0.0f,
                           -1.0f, 0.0f,
                           -1.0f, -1.0f,
                           0.0f, -1.0f};

            VxVertexAttrib points = new VxVertexAttrib(pts, 2);
            vp.setVertexAttrib("position", points);

            float cls[] = { 0.0f, .3f, 1.0f,
                            0.5f, 0.3f, 1.0f,
                            0.0f, 1.0f, 1.0f,
                            0.0f, 1.0f, 1.0f};
            VxVertexAttrib colors = new VxVertexAttrib(cls, 3);
            vp.setVertexAttrib("color", colors);

            float ident[][] = {{1.0f, 0.0f, 0.0f, 0.0f},
                               {0.0f, 1.0f, 0.0f, 0.0f},
                               {0.0f, 0.0f, 1.0f, 0.0f},
                               {0.0f, 0.0f, 0.0f, 1.0f}};

            vp.setUniform("proj", ident);


            int idxs[] = {0,1,2,
                          2,3,0};
            VxIndexData index = new VxIndexData(idxs);
            vp.setElementArray(index, Vx.GL_TRIANGLES);

            vw.getBuffer("first-buffer").stage(vp);
        }


        vw.getBuffer("first-buffer").commit();









        JFrame jf = new JFrame();

        JImage jim = new JImage();
        jf.add(jim);
        jf.setSize(width,height+22);
        jf.setVisible(true);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);


        for (int i = 0; i < 2; i++) {
            vxls.render(width,height);

            BufferedImage im = new BufferedImage(width,height, BufferedImage.TYPE_3BYTE_BGR);
            byte img[] = ((DataBufferByte) (im.getRaster().getDataBuffer())).getData();
            vxls.read_pixels(width,height,img);

            jim.setImage(im);
            System.out.printf("Render %d \n",i);

            TimeUtil.sleep(1000);
        }
    }
}