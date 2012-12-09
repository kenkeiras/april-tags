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
        GetOpt opts  = new GetOpt();

        opts.addBoolean('h',"help",false,"See this help screen");
        opts.addString('u',"url","java://?width=480&height=480","Which VxRenderer to use");
        opts.addString('i',"img-path","/home/jhstrom/Desktop/BlockM.png","Which image to display as a texture?");

        if (!opts.parse(args) || opts.getBoolean("help") || opts.getExtraArgs().size() > 0) {
            opts.doHelp();
            return;
        }


        VxRenderer vxr = VxRenderer.make(opts.getString("url"));//width,height);


        VxWorld vw = new VxWorld(vxr);
        VxLayer vl = new VxLayer(vxr, vw);
        vl.set_viewport(new float[]{0,0,.5f,.5f});

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

        // Now do Texture:
        ArrayList<VxObject> progs1 = new ArrayList();
        try {
            BufferedImage img = ImageUtil.convertImage(ImageIO.read(new File(opts.getString("img-path"))), BufferedImage.TYPE_3BYTE_BGR);
            VxTexture vtex = new VxTexture(img);

            VxProgram vp = VxProgram.make("texture");
            vp.setVertexAttrib("position", point_attribs.get(2));
            vp.setTexture("texture", vtex); //XXX Error!

            float texcoords[] = {
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f};

            vp.setVertexAttrib("texIn", new VxVertexAttrib(texcoords,2));
            vp.setElementArray(index, Vx.GL_TRIANGLES);

            progs1.add(new VxChain(LinAlg.translate(-1,-1), vp));

        } catch(IOException e) {
            System.out.println("Texture Ex: "+e);
        }

        for (int i = 0; i < 4; i+=2) {


            VxProgram vp = VxProgram.make("multi-colored");//new VxProgram(vertRx,fragRx);
            vp.setVertexAttrib("position", point_attribs.get(i));

            vp.setVertexAttrib("color", color_attribs.get(i));

            vp.setElementArray(index, Vx.GL_TRIANGLES);

            progs1.add(vp);
        }

        ArrayList<VxObject> progs2 = new ArrayList();
        for (int i = 1; i < 4; i+=2) { // XXX Only render 1
            VxProgram vp = VxProgram.make("multi-colored");
            vp.setVertexAttrib("position", point_attribs.get(i));

            vp.setVertexAttrib("color", color_attribs.get(i));

            vp.setElementArray(index, Vx.GL_TRIANGLES);

            progs2.add(vp);
        }


        // DEbug:
        // progs2.clear();
        // progs1.clear();

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
            vw.getBuffer("points").setDrawOrder(10);
            vw.getBuffer("points").commit();
        }




        if (vxr instanceof VxLocalRenderer) {
            JFrame jf = new JFrame();


            int canvas_size[] = vxr.get_canvas_size();
            int width = canvas_size[0], height = canvas_size[1];

            VxCanvas vc = new VxCanvas(((VxLocalRenderer)vxr));
            jf.add(vc);
            jf.setSize(width,height+22);
            jf.setVisible(true);
            jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        }

        vw.getBuffer("first-buffer").setDrawOrder(-4);
        vw.getBuffer("second-buffer").setDrawOrder(-9);

        // Render loop
        for (int i = 0; ; i++) {

            int type = i % 6;
            switch(type) {
                case 0:
                    for (VxObject vp : progs1)
                        vw.getBuffer("first-buffer").stage(vp);
                    break;
                case 1:
                case 3:
                    for (VxObject vp : progs2)
                        vw.getBuffer("second-buffer").stage(vp);
                    for (VxObject vp : progs1)
                        vw.getBuffer("first-buffer").stage(vp);
                    break;
                case 2:
                    for (VxObject vp : progs2)
                        vw.getBuffer("second-buffer").stage(vp);
                    break;
            }
            vw.getBuffer("first-buffer").commit();
            vw.getBuffer("second-buffer").commit();

            TimeUtil.sleep(500);
        }
    }
}
