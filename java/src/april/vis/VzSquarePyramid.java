package april.vis;

import java.awt.*;

/* A square-base pyramid; base spans from -1 to +1 in the XY plane
 * (with z = 0), and apex rises up z axis to (0,0,1).
 *
 * Can also be used as a viewing frustrum, where M is the model view
 * matrix, d is a large number (to scale the size of the viewing
 * frustrum, and fovx and fovy are the fields of view of the camera in
 * degrees. (See CameraPosition and VisUtil.computeFieldOfViewX).

      tmp = new VisChain(LinAlg.inverse(M),
                         LinAlg.scale(d, d, d),
                         LinAlg.translate(0, 0, -1),
                         LinAlg.scale(Math.tan(Math.toRadians(fovx/2)),
                                      Math.tan(Math.toRadians(fovy/2)),
                                      1),
                         new VzSquarePyramid(new VzMesh.Style(new Color(0,255,255,100))));

 **/
public class VzSquarePyramid implements VisObject
{
    Style styles[];
    int flags;

    public static final int BOTTOM = 1;

    static VzMesh mesh;
    static VzSquare square;

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

        square = new VzSquare(2, 2);
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
}
