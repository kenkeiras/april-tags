package april.vis;

import java.awt.*;

/* A square-base pyramid; base spans from -1 to +1 in the XY plane
 * (with z = 0), and apex rises up z axis to (0,0,1) **/
public class VzSquarePyramid implements VisObject
{
    static final float sqrt2 = (float) Math.sqrt(2);

    static float vd[] = new float[] { 1, -1, 0,
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
                                      0, 0, 1,

                                      // the base
                                      -1, -1, 0,
                                      -1, 1, 0,
                                      1, 1, 0,

                                      -1, -1, 0,
                                      1, 1, 0,
                                      1, -1, 0
    };

    static float nd[] = new float[] { sqrt2, 0, sqrt2,
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
                                      0, -sqrt2, sqrt2,

                                      0, 0, -1,
                                      0, 0, -1,
                                      0, 0, -1,

                                      0, 0, -1,
                                      0, 0, -1,
                                      0, 0, -1 };

    static long vdid = VisUtil.allocateID();
    static long ndid = VisUtil.allocateID();

    VisAbstractFillStyle fillStyle;
    boolean hasBottom;

    public VzSquarePyramid(VisAbstractFillStyle fillStyle, boolean hasBottom)
    {
        this.fillStyle = fillStyle;
        this.hasBottom = hasBottom;
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        fillStyle.bindFill(gl);
        gl.gldBind(GL.VBO_TYPE_VERTEX, vdid, vd.length / 3, 3, vd);
        gl.gldBind(GL.VBO_TYPE_NORMAL, ndid, nd.length / 3, 3, nd);

        if (hasBottom)
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, vd.length / 3);
        else
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, vd.length / 3 - 6);

        gl.gldUnbind(GL.VBO_TYPE_VERTEX, vdid);
        gl.gldUnbind(GL.VBO_TYPE_NORMAL, ndid);
        fillStyle.unbindFill(gl);
    }
}
