package april.vis;

import java.awt.*;

public class VisCamera implements VisObject
{
    static Color defaultFill;

    public VisCamera()
    {
        this(defaultFill);
    }

    public VisCamera(Color fill)
    {

    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        assert(false);
    }

}