package april.vis;

import java.awt.*;

/** Controls the rendering of a solid object. **/
public class VisFillStyle implements VisAbstractFillStyle
{
    Color c;

    // TODO: Eventually, add material properties.

    public VisFillStyle(Color c)
    {
        this.c = c;
    }

    public synchronized void bindFill(GL gl)
    {
        gl.glColor(c);
    }

    public synchronized void unbindFill(GL gl)
    {
        // nothing to do.
    }
}
