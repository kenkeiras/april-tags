package april.vis;

import java.awt.*;

public class VzStar implements VisObject
{
    final static Color defaultFill=Color.yellow;
    final static Color defaultBorder=Color.white;

    public VzStar()
    {
        this(defaultFill, defaultBorder);
    }


    public VzStar(Color fill)
    {
        this(fill, defaultBorder);
    }

    public VzStar(Color fill, Color border)
    {

    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        assert(false);
    }

}