package april.vis;

import java.awt.*;

public class VzCamera implements VisObject
{
    static Color defaultFill;

    public VzCamera()
    {
        this(defaultFill);
    }

    public VzCamera(Color fill)
    {

    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        assert(false);
    }

}