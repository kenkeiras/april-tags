package april.vis;

import java.awt.*;
import java.io.*;

/* A square-base pyramid; base spans from -1 to +1 in the XY plane
 * (with z = 0), and apex rises up z axis to (0,0,1) **/
public class VzSquarePyramid implements VisObject, VisSerializable
{
    Style styles[];
    int flags;

    public static final int BOTTOM = 1;

    static VzMesh mesh;
    static VzRectangle square;

    static {
        final float sqrt2 = (float) Math.sqrt(2);

        float vd[] = new float[] { 1, -1, 0,
                                   1, 1, 0,
                                   0, 0, 1,

                                   1, 1, 0,
                                   -1, 1, 0,
                                   0, 0, 1,

                                   -1, 1, 0,
                                   -1, -1, 0,
                                   0, 0, 1,

                                   -1, -1, 0,
                                   1, -1, 0,
                                   0, 0, 1,        };

        VisVertexData fillVertices = new VisVertexData(vd, vd.length / 3, 3);

        float nd[] = new float[] { sqrt2, 0, sqrt2,
                                   sqrt2, 0, sqrt2,
                                   sqrt2, 0, sqrt2,

                                   0, sqrt2, sqrt2,
                                   0, sqrt2, sqrt2,
                                   0, sqrt2, sqrt2,

                                   -sqrt2, 0, sqrt2,
                                   -sqrt2, 0, sqrt2,
                                   -sqrt2, 0, sqrt2,

                                   0, -sqrt2, sqrt2,
                                   0, -sqrt2, sqrt2,
                                   0, -sqrt2, sqrt2 };

        VisVertexData fillNormals = new VisVertexData(nd, nd.length / 3, 3);
        mesh = new VzMesh(fillVertices, fillNormals, VzMesh.TRIANGLES);

        square = new VzRectangle(2, 2);
    }

    public VzSquarePyramid(Style ... styles)
    {
        this(BOTTOM, styles);
    }

    public VzSquarePyramid(int flags, Style ... styles)
    {
        this.flags = flags;
        this.styles = styles;
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl, Style style)
    {
        if (style instanceof VzMesh.Style) {
            mesh.render(vc, layer, rinfo, gl, (VzMesh.Style) style);

            if ((flags & BOTTOM) != 0) {
                square.render(vc, layer, rinfo, gl, (VzMesh.Style) style);
            }
        }
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        for (Style style : styles)
            render(vc, layer, rinfo, gl, style);
    }

    public VzSquarePyramid(ObjectReader ins)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeInt(flags);

        outs.writeInt(styles.length);
        for (int sidx = 0; sidx < styles.length; sidx++)
            outs.writeObject(styles[sidx]);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        flags = ins.readInt();

        int nstyles = ins.readInt();
        styles = new Style[nstyles];
        for (int sidx = 0; sidx < styles.length; sidx++)
            styles[sidx] = (Style) ins.readObject();

    }
}
