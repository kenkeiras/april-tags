package april.vis;

import java.awt.*;

/** Renders a robot as a small triangle. **/
public class VzRobot implements VisObject
{
    Style styles[];

    final static float length = 1.0f;
    final static float width = .45f;

    static VzMesh mesh;
    static VzLines lines;

    static {
        VisVertexData vd = new VisVertexData(new float[] { -length/2, width/2, 0 },
                                             new float[] { length/2,  0, 0 },
                                             new float[] { -length/2, -width/2, 0 });

        mesh = new VzMesh(vd, VzMesh.TRIANGLES);
        lines = new VzLines(vd, VzLines.LINE_LOOP);
    }

    public VzRobot()
    {
        this(new VzLines.Style(Color.cyan, 1), new VzMesh.Style(Color.blue));
    }

    public VzRobot(Color c)
    {
        this(new VzMesh.Style(c));
    }

    public VzRobot(Style ... styles)
    {
        this.styles = styles;
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        for (Style style : styles) {
            if (style instanceof VzLines.Style)
                lines.render(vc, layer, rinfo, gl, (VzLines.Style) style);
            if (style instanceof VzMesh.Style)
                mesh.render(vc, layer, rinfo, gl, (VzMesh.Style) style);
        }
    }
}
