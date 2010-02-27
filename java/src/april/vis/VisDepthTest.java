package april.vis;

import javax.media.opengl.*;
import javax.media.opengl.glu.*;

import april.jmat.geom.*;

import java.util.*;

/** VisObject wrapper that manipulates whether depth testing is performed. **/
public class VisDepthTest implements VisObject
{
    VisObject os[];
    boolean enable;

    public VisDepthTest(boolean enable, VisObject ... os)
    {
        this.enable = enable;
        this.os = os;
    }

    public void render(VisContext vc, GL gl, GLU glu)
    {
        if (enable)
            gl.glEnable(GL.GL_DEPTH_TEST);
        else
            gl.glDisable(GL.GL_DEPTH_TEST);

        for (VisObject vo : os)
            vo.render(vc, gl, glu);

        gl.glEnable(GL.GL_DEPTH_TEST);
    }
}
