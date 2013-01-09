package april.vx;

import java.awt.Color;
import java.util.*;

public class VxLines implements VxObject
{

    // Two constructors -- 1) for constant color
    //                     2) for per-vertex-color
    VxProgram vp;


    // type: One of Vx.VX_LINES, Vx.VX_LINE_STRIP, Vx.VX_LINE_LOOP
    //
    public VxLines(VxVertexAttrib points, int type, Color c, double wd)
    {
        vp = VxProgram.make("single-color");

        vp.setVertexAttrib("position", points);
        vp.setUniform("color", c.getRGBComponents(null));
        vp.setLineWidth(wd);


        vp.setDrawArray(points.size(), type);
    }

    public VxLines(VxVertexAttrib points, int type, VxVertexAttrib colors, double wd)
    {
        vp = VxProgram.make("multi-colored");

        vp.setVertexAttrib("position", points);
        vp.setVertexAttrib("color", colors);
        vp.setLineWidth(wd);

        vp.setDrawArray(points.size(), type);
    }

    public void appendTo(HashSet<VxResource> resources, VxCodeOutputStream codes, MatrixStack ms)
    {
        vp.appendTo(resources, codes, ms);
    }
}