package april.vis;

import java.awt.*;

public class VzSquare implements VisObject
{
    double sx, sy;

    Style styles[];

    static VzLines lines;
    static VzMesh  mesh;

    static {

        // vertex data for GL_QUADS and for LINE LOOP
        float vd[] = new float[] { -1, -1,
                                   1, -1,
                                   1, 1,
                                   -1, 1 };

        VisVertexData fillVertices = new VisVertexData(vd, vd.length / 2, 2);

        // normal data for GL_QUADS
        float nd[] = new float[] { 0, 0, 1,
                                   0, 0, 1,
                                   0, 0, 1,
                                   0, 0, 1 };

        VisVertexData fillNormals = new VisVertexData(nd, nd.length / 3, 3);

        mesh = new VzMesh(fillVertices, fillNormals, VzMesh.QUADS);

        lines = new VzLines(fillVertices, VzLines.LINE_LOOP);
    }

    /** A box that extends from -1 to +1 along x, and y axis **/
    public VzSquare(Style ... styles)
    {
        this(1, 1, styles);
    }

    /** A box that extends from -sx/2 to sx/2, -sy/2 to sy/2 **/
    public VzSquare(double sx, double sy, Style ... styles)
    {
        this.sx = sx / 2;
        this.sy = sy / 2;
        this.styles = styles;
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl, Style style)
    {
        gl.glPushMatrix();
        gl.glScaled(sx, sy, 1);

        if (style instanceof VzMesh.Style)
            mesh.render(vc, layer, rinfo, gl, (VzMesh.Style) style);

/*            fillStyle.bindFill(gl);
            gl.gldBind(GL.VBO_TYPE_VERTEX, vdid, vd.length / 3, 3, vd);
            gl.gldBind(GL.VBO_TYPE_NORMAL, ndid, nd.length / 3, 3, nd);

            gl.glDrawArrays(GL.GL_QUADS, 0, vd.length / 3);

            gl.gldUnbind(GL.VBO_TYPE_VERTEX, vdid);
            gl.gldUnbind(GL.VBO_TYPE_NORMAL, ndid);
            fillStyle.unbindFill(gl);
        }
*/
        if (style instanceof VzLines.Style)
            lines.render(vc, layer, rinfo, gl, (VzLines.Style) style);

        gl.glPopMatrix();
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        for (Style style : styles)
            render(vc, layer, rinfo, gl, style);
    }

}
