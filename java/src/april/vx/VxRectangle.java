package april.vx;

import java.awt.Color;
import java.util.*;
import april.jmat.*;

public class VxRectangle implements VxObject
{
    static final VxResource vertices = new VxResource(new float[] {-1, -1,
                                                                   1, -1,
                                                                   1, 1,
                                                                   -1, 1 });
    static final VxResource normals = new VxResource(new float[] { 0, 0, 1,
                                                                   0, 0, 1,
                                                                   0, 0, 1,
                                                                   0, 0, 1 });

    static final VxResource indices = new VxResource(new int[]{0,1,2,
                                                               2,3,0});

    VxChain chain = new VxChain();

    // Each of the colors can be null
    public VxRectangle(double _sx, double _sy, Color lineColor, double line_width, Color fillColor)
    {
        chain.add(LinAlg.scale(_sx, _sy, 1));


        if (fillColor != null) {
            chain.add(new VxMesh(new VxVertexAttrib(vertices, 2), indices, fillColor));
        }

        if (lineColor != null) {
            chain.add(new VxLines(new VxVertexAttrib(vertices, 2), Vx.GL_LINE_LOOP, lineColor, line_width));
        }
    }


    public void appendTo(HashSet<VxResource> resources, VxCodeOutputStream codes, MatrixStack ms)
    {
        chain.appendTo(resources, codes, ms);
    }

}