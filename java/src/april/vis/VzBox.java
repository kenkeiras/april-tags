package april.vis;

import java.awt.*;

public class VisBox implements VisObject
{

    public VisBox(double sx, double sy, double sz,
                  Color fill)
    {
        this(sx,sy,sz,fill, null);
    }

    public VisBox(double sx, double sy, double sz,
                  Color fill, Color border)
    {

    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        assert(false);
    }

}