package april.vx;

import java.awt.Color;
import java.util.*;

public class VxPoints implements VxObject
{


    // Two constructors -- 1) for constant color
    //                     2) for per-vertex-color
    VxProgram vp;

    public VxPoints(VxVertexAttrib points, Color c, double size)
    {
        vp = VxProgram.make("single-color");

        vp.setVertexAttrib("position", points);
        vp.setUniform("color", c.getRGBComponents(null));
        vp.setLineWidth(size);


        vp.setDrawArray(points.size(), Vx.GL_POINTS);
    }

    public VxPoints(VxVertexAttrib points, VxVertexAttrib colors, double size)
    {
        vp = VxProgram.make("multi-colored");

        vp.setVertexAttrib("position", points);
        vp.setVertexAttrib("color", colors);
        vp.setLineWidth(size);

        vp.setDrawArray(points.size(), Vx.GL_POINTS);
    }

    public void appendTo(HashSet<VxResource> resources, VxCodeOutputStream codes, MatrixStack ms)
    {
        vp.appendTo(resources, codes, ms);
    }
}