package april.vx;

import java.util.*;

public class VxImage implements VxObject
{
    VxProgram prog = VxProgram.make("texture");


    public static final int FLIP = 1;

    static final VxResource indices = new VxResource(new int[]{0,1,2,
                                                               2,3,0});

    public VxImage(VxTexture tex, int flags)
    {
        System.out.printf("w/h %d/%d\n",tex.width, tex.height);
        // Each image will have a different dimension, so different position is called for:
        float position[] = new float[]{ 0.0f, 0.0f,
                                        tex.width, 0.0f,
                                        tex.width, tex.height,
                                        0.0f,  tex.height}; // XXX Could do this with a scale?


        float texcoords[][] =  new float[][]{{0.0f, 0.0f},
                                             {1.0f, 0.0f},
                                             {1.0f, 1.0f},
                                             {0.0f, 1.0f}};


        if ((flags & FLIP) != 0) {
            for (int i = 0; i < texcoords.length; i++)
                texcoords[i][1] = 1 - texcoords[i][1];
        }

        prog.setVertexAttrib("position", new VxResource(position), 2);
        prog.setVertexAttrib("texIn", new VxVertexAttrib(Arrays.asList(texcoords)));
        prog.setTexture("texture", tex);
        prog.setElementArray(indices, Vx.GL_TRIANGLES);
    }

    public void appendTo(HashSet<VxResource> resources, VxCodeOutputStream codes, MatrixStack ms)
    {
        prog.appendTo(resources, codes, ms);
    }
}