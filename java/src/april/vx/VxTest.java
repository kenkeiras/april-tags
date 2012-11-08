package april.vx;

import javax.swing.*;
import april.util.*;
import java.awt.image.*;

public class VxTest
{

    public static void main(String args[])
    {
        int width = 480, height = 480;

        VxProgram vp = new VxProgram("foo".getBytes(), VxUtil.allocateID(),
                                     "bar".getBytes(), VxUtil.allocateID());

        float pts[] = { 1.0f, 1.0f,
                        0.0f, 1.0f,
                        0.0f, 0.0f,
                        1.0f, 0.0f};

        VxVertexAttrib points = new VxVertexAttrib(pts, 2);
        vp.setVertexAttrib("position", points);


        int idxs[] = {0,1,2,
                      2,3,0};
        VxIndexData index = new VxIndexData(idxs);
        vp.setElementArray(index, Vx.GL_TRIANGLES);


        VxLocalServer vxls = new VxLocalServer(width,height);
        VxWorld vw = new VxWorld(vxls);

        vw.getBuffer("first-buffer").stage(vp);
        vw.getBuffer("first-buffer").commit();


        vxls.render(width,height);

        BufferedImage im = new BufferedImage(width,height, BufferedImage.TYPE_3BYTE_BGR);
        byte img[] = ((DataBufferByte) (im.getRaster().getDataBuffer())).getData();
        vxls.read_pixels(width,height,img);


        JFrame jf = new JFrame();

        JImage jim = new JImage();
        jf.add(jim);
        jf.setSize(width,height+22);
        jf.setVisible(true);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        jim.setImage(im);
    }
}