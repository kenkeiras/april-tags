package april.vis;

import java.awt.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

import april.jmat.*;

/** Loads and represents an object in OFF format **/
public class VzOFF implements VisObject
{
    FloatArray vertexArray = new FloatArray();
    IntArray indexArray = new IntArray();
    FloatArray normalArray;

    long vid = VisUtil.allocateID();
    long nid = VisUtil.allocateID();
    long iid = VisUtil.allocateID();

    VzMesh.Style style;

    public VzOFF(String path, VzMesh.Style style) throws IOException
    {
        this.style = style;

        BufferedReader ins = new BufferedReader(new FileReader(new File(path)));

        String header = ins.readLine();
        if (!header.equals("OFF"))
            throw new IOException("Not an OFF file");

        int nvertexArray, nfaces, nedges;
        if (true) {
            String sizes = ins.readLine();
            String toks[] = sizes.split("\\s+");
            nvertexArray = Integer.parseInt(toks[0]);
            nfaces = Integer.parseInt(toks[1]);
            nedges = Integer.parseInt(toks[2]);
        }

        for (int i = 0; i < nvertexArray; i++) {
            String line = ins.readLine();
            String toks[] = line.split("\\s+");
            vertexArray.add(Float.parseFloat(toks[0]));
            vertexArray.add(Float.parseFloat(toks[1]));
            vertexArray.add(Float.parseFloat(toks[2]));
        }

        float vs[] = vertexArray.getData();
        float ns[] = new float[vs.length];
        normalArray = new FloatArray(ns);

        for (int i = 0; i < nfaces; i++) {
            String line = ins.readLine();
            String toks[] = line.split("\\s+");

            int len = Integer.parseInt(toks[0]);
            assert(len+1 == toks.length);

            for (int j = 2; j+1 <= len; j++) {
                int a = Integer.parseInt(toks[1]);
                int b = Integer.parseInt(toks[j]);
                int c = Integer.parseInt(toks[j+1]);

                indexArray.add(a);
                indexArray.add(b);
                indexArray.add(c);

                float vba[] = new float[] { vs[b*3+0] - vs[a*3+0],
                                            vs[b*3+1] - vs[a*3+1],
                                            vs[b*3+2] - vs[a*3+2] };

                float vca[] = new float[] { vs[c*3+0] - vs[a*3+0],
                                            vs[c*3+1] - vs[a*3+1],
                                            vs[c*3+2] - vs[a*3+2] };

                float n[] = LinAlg.crossProduct(vba, vca);

                for (int k = 0; k < 3; k++) {
                    ns[3*a+k] += n[k];
                    ns[3*b+k] += n[k];
                    ns[3*c+k] += n[k];
                }
            }
        }

        for (int i = 0; i+2 < ns.length; i+=3) {
            double mag = Math.sqrt(ns[i+0]*ns[i+0] + ns[i+1]*ns[i+1] + ns[i+2]*ns[i+2]);
            ns[i+0] /= mag;
            ns[i+1] /= mag;
            ns[i+2] /= mag;
        }

        ins.close();
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        gl.glColor(Color.blue);

//        gl.glEnable(GL.GL_CULL_FACE);

        gl.gldBind(GL.VBO_TYPE_VERTEX, vid, vertexArray.size() / 3, 3, vertexArray.getData());
        gl.gldBind(GL.VBO_TYPE_NORMAL, nid, normalArray.size() / 3, 3, normalArray.getData());
        gl.gldBind(GL.VBO_TYPE_ELEMENT_ARRAY, iid, indexArray.size(), 1, indexArray.getData());

        gl.glDrawRangeElements(GL.GL_TRIANGLES,
                               0, vertexArray.size()/3, indexArray.size(),
                               0);

        gl.gldUnbind(GL.VBO_TYPE_VERTEX, vid);
        gl.gldUnbind(GL.VBO_TYPE_NORMAL, nid);
        gl.gldUnbind(GL.VBO_TYPE_ELEMENT_ARRAY, iid);

//        gl.glDisable(GL.GL_CULL_FACE);
    }

    public static void main(String args[])
    {
        JFrame f = new JFrame("VzOFF "+args[0]);
        f.setLayout(new BorderLayout());

        VisWorld vw = new VisWorld();
        VisLayer vl = new VisLayer(vw);
        VisCanvas vc = new VisCanvas(vl);

        try {
            VzOFF model = new VzOFF(args[0], new VzMesh.Style(Color.red));

            System.out.printf("loaded %d vertexArray, %d triangle indexArray\n", model.vertexArray.size()/3, model.indexArray.size()/3);

            VisWorld.Buffer vb = vw.getBuffer("model");
            vb.addBack(model);

            if (false) {
                for (int x = 0; x < 10; x++) {
                    for (int y = 0; y < 10; y++) {
                        vb.addBack(new VisChain(LinAlg.translate(x*10, y*10, 0),
                                                model));
                    }
                }
            }

            vb.swap();
        } catch (IOException ex) {
            System.out.println("ex: "+ex);
        }

        f.add(vc);
        f.setSize(600, 400);
        f.setVisible(true);

    }

}
