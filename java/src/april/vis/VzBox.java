package april.vis;

import java.awt.*;

public class VzBox implements VisObject
{
    double sx, sy, sz;
    VisAbstractFillStyle fillStyle;

    // vertex data for GL_QUADS
    static float vd[] = new float[] { -1, 1, -1,
                                      1, 1, -1,
                                      1, -1, -1,
                                      -1, -1, -1,

                                      -1, -1, -1,
                                      1, -1, -1,
                                      1, -1, 1,
                                      -1, -1, 1,

                                      -1, -1, 1,
                                      -1, 1, 1,
                                      -1, 1, -1,
                                      -1, -1, -1,

                                      1, 1, 1,
                                      1, -1, 1,
                                      1, -1, -1,
                                      1, 1, -1,

                                      1, 1, -1,
                                      -1, 1, -1,
                                      -1, 1, 1,
                                      1, 1, 1,

                                      -1, -1, 1,
                                      1, -1, 1,
                                      1, 1, 1,
                                      -1, 1, 1
    };

    // normal data for GL_QUADS
    static float nd[] = new float[] { 0, 0, -1,
                                      0, 0, -1,
                                      0, 0, -1,
                                      0, 0, -1,

                                      0, 1, 0,
                                      0, 1, 0,
                                      0, 1, 0,
                                      0, 1, 0,

                                      1, 0, 0,
                                      1, 0, 0,
                                      1, 0, 0,
                                      1, 0, 0,

                                      1, 0, 0,
                                      1, 0, 0,
                                      1, 0, 0,
                                      1, 0, 0,

                                      0, 1, 0,
                                      0, 1, 0,
                                      0, 1, 0,
                                      0, 1, 0,

                                      0, 0, 1,
                                      0, 0, 1,
                                      0, 0, 1,
                                      0, 0, 1
    };


    // GL_LINES
    static float lvf[] = new float[] { -1, -1, -1, // a
                                       1, -1, -1,  // b
                                       1, -1, -1,  // b
                                       1, 1, -1,   // c
                                       1, 1, -1,   // c
                                       -1, 1, -1,  // d
                                       -1, 1, -1,  // d
                                       -1, -1, -1, // a

                                       -1, -1, 1,  // a
                                       1, -1, 1,   // b
                                       1, -1, 1,   // b
                                       1, 1, 1,    // c
                                       1, 1, 1,    // c
                                       -1, 1, 1,   // d
                                       -1, 1, 1,   // d
                                       -1, -1, 1,  // a

                                       -1, -1, -1,
                                       -1, -1, 1,
                                       1, -1, -1,
                                       1, -1, 1,
                                       1, 1, -1,
                                       1, 1, 1,
                                       -1, 1, -1,
                                       -1, 1, 1
    };

    static long vdid = VisUtil.allocateID();
    static long ndid = VisUtil.allocateID();
    static long vlid = VisUtil.allocateID();

    /** A box that extends from -1 to +1 along x, y, and z axis **/
    public VzBox(VisAbstractFillStyle fillStyle)
    {
        this(1, 1, 1, fillStyle);
    }

    public VzBox(double sx, double sy, double sz, Color color)
    {
        this(sx, sy, sz, new VisFillStyle(color));
    }

    /** A box that extends from -sx/2 to sx/2, -sy/2 to sy/2, -sz/2 to sz/2 **/
    public VzBox(double sx, double sy, double sz, VisAbstractFillStyle fillStyle)
    {
        this.sx = sx / 2;
        this.sy = sy / 2;
        this.sz = sz / 2;
        this.fillStyle = fillStyle;
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        gl.glPushMatrix();
        gl.glScaled(sx, sy, sz);

        fillStyle.bindFill(gl);
        gl.gldBind(GL.VBO_TYPE_VERTEX, vdid, vd.length / 3, 3, vd);
        gl.gldBind(GL.VBO_TYPE_NORMAL, ndid, nd.length / 3, 3, nd);

        gl.glDrawArrays(GL.GL_QUADS, 0, vd.length / 3);

        gl.gldUnbind(GL.VBO_TYPE_VERTEX, vdid);
        gl.gldUnbind(GL.VBO_TYPE_NORMAL, ndid);
        fillStyle.unbindFill(gl);

        gl.glPopMatrix();
    }

}
