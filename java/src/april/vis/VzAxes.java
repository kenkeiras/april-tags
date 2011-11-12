package april.vis;

import april.jmat.*;
import java.awt.*;

public class VzAxes implements VisObject
{
    static float vd[] = new float[] { 0, 0, 0,
                                      1, 0, 0,

                                      0, 0, 0,
                                      0, 1, 0,

                                      0, 0, 0,
                                      0, 0, 1
    };

    static int cd[] = new int[] { 0xff0000ff,
                                  0xff0000ff,
                                  0xff00ff00,
                                  0xff00ff00,
                                  0xffff0000,
                                  0xffff0000
    };

    static final long vdid = VisUtil.allocateID();
    static final long cdid = VisUtil.allocateID();

    public VzAxes()
    {
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        gl.glLineWidth(3.0f);

        gl.gldBind(GL.VBO_TYPE_VERTEX, vdid, vd.length / 3, 3, vd);
        gl.gldBind(GL.VBO_TYPE_COLOR, cdid, cd.length, 4, cd);

        gl.glDrawArrays(GL.GL_LINES, 0, vd.length / 3);

        gl.gldUnbind(GL.VBO_TYPE_VERTEX, vdid);
        gl.gldUnbind(GL.VBO_TYPE_COLOR, cdid);

        double s = 0.1;

        VisChain c = new VisChain(new VisChain(LinAlg.translate(1, 0, 0),
                                               LinAlg.rotateY(Math.PI/2),
                                               LinAlg.scale(s/2, s/2, s),
                                               new VzSquarePyramid(new VisFillStyle(Color.red), true)),
                                  new VisChain(LinAlg.translate(0, 1, 0),
                                               LinAlg.rotateX(-Math.PI/2),
                                               LinAlg.scale(s/2, s/2, s),
                                               new VzSquarePyramid(new VisFillStyle(Color.green), true)),
                                  new VisChain(LinAlg.translate(0, 0, 1),
                                               LinAlg.scale(s/2, s/2, s),
                                               new VzSquarePyramid(new VisFillStyle(Color.blue), true)));

        c.render(vc, layer, rinfo, gl);
    }

}
