package april.vx;

import java.awt.Color;
import java.util.*;

public class VxMesh implements VxObject
{


    // Two constructors -- 1) for constant color
    //                     2) for per-vertex-color
    VxProgram vp;

    // XXX Normals, lighting
    public VxMesh(VxVertexAttrib points, VxIndexData indices, Color c)
    {
        vp = VxProgram.make("single-color");

        vp.setVertexAttrib("position", points);
        vp.setUniform("color", c.getRGBComponents(null));


        vp.setElementArray(indices, Vx.GL_TRIANGLES);
    }

    public VxMesh(VxVertexAttrib points, VxIndexData indices, VxVertexAttrib colors)
    {
        vp = VxProgram.make("multi-colored");

        vp.setVertexAttrib("position", points);
        vp.setVertexAttrib("color", colors);

        vp.setElementArray(indices, Vx.GL_TRIANGLES);
    }

    public void appendTo(HashSet<VxResource> resources, VxCodeOutputStream codes, MatrixStack ms)
    {
        vp.appendTo(resources, codes, ms);
    }
}