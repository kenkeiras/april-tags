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
        opts.addString('u',"url","java://","Which VxRenderer to use");
        opts.addString('i',"img-path","/home/jhstrom/Desktop/BlockM.png","Which image to display as a texture?");

        if (!opts.parse(args) || opts.getBoolean("help") || opts.getExtraArgs().size() > 0) {
            opts.doHelp();
            return;
        }


        VxRenderer vxr = VxRenderer.make(opts.getString("url"));//width,height);

        int canvas_size[] = vxr.get_canvas_size();
        int width = canvas_size[0], height = canvas_size[1];



        VxWorld vw = new VxWorld(new VxResourceManager(vxr));

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
        {
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

        }

        for (int i = 0; i < 4; i+=2) {


            VxProgram vp = VxProgram.make("multi-colored");//new VxProgram(vertRx,fragRx);
            vp.setVertexAttrib("position", point_attribs.get(i));

            vp.setVertexAttrib("color", color_attribs.get(i));

            // vp.setUniform("proj", proj);

            vp.setElementArray(index, Vx.GL_TRIANGLES);

            progs1.add(vp);
        }

        ArrayList<VxObject> progs2 = new ArrayList();
        for (int i = 1; i < 4; i+=2) { // XXX Only render 1
            VxProgram vp = VxProgram.make("multi-colored");
            vp.setVertexAttrib("position", point_attribs.get(i));

            vp.setVertexAttrib("color", color_attribs.get(i));

            // vp.setUniform("proj", proj);

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
            vw.getBuffer("points").commit();
        }




        if (vxr instanceof VxLocalRenderer) {
            double proj_d[][] = LinAlg.matrixAB(VxUtil.gluPerspective(60.0f, width*1.0f/height, 0.1f, 5000.0f),
                                                VxUtil.lookAt(new double[]{0,0,10}, new double[3], new double[]{0,1,0}));

            float proj[][] = VxUtil.copyFloats(proj_d);
            ((VxLocalRenderer)vxr).set_system_pm_matrix(proj);

            JFrame jf = new JFrame();


            VxCanvas vc = new VxCanvas(((VxLocalRenderer)vxr));
            jf.add(vc);
            jf.setSize(width,height+22);
            jf.setVisible(true);
            jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        }

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