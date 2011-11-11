package april.vis;

import java.awt.*;

public class VisRobot implements VisObject
{
    final static Color defaultFill;
    final static Color defaultBorder;

    public VisRobot()
    {
        this(defaultFill, defaultBorder);
    }


    public VisRobot(Color fill)
    {
        this(fill, defaultBorder);
    }

    public VisRobot(Color fill, Color border)
    {

    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        assert(false);
    }

}