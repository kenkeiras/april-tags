package april.vis;

import javax.media.opengl.*;
import javax.media.opengl.glu.*;

import april.jmat.geom.*;

import java.util.*;

/** VisObject wrapper that manipulates whether lighting is performed. **/
public class VisLighting implements VisObject
{
    VisObject os[];
    boolean enable;

    public VisLighting(boolean enable, VisObject ... os)
    {
        this.enable = enable;
        this.os = os;
    }

    public void render(VisContext vc, GL gl, GLU glu)
    {
        if (enable)
            gl.glEnable(GL.GL_LIGHTING);
        else
            gl.glDisable(GL.GL_LIGHTING);

        for (VisObject vo : os)
            vo.render(vc, gl, glu);

        /// XXX bug: should restore previous state.
        gl.glEnable(GL.GL_LIGHTING);
    }
}
