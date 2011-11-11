package april.vis;

import java.awt.*;

public class VisStar implements VisObject
{
    final static Color defaultFill;
    final static Color defaultBorder;

    public VisStar()
    {
        this(defaultFill, defaultBorder);
    }


    public VisStar(Color fill)
    {
        this(fill, defaultBorder);
    }

    public VisStar(Color fill, Color border)
    {

    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        assert(false);
    }

}