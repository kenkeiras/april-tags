package april.vis;

import java.awt.*;

public class VzRobot implements VisObject
{
    final static Color defaultFill = Color.blue;
    final static Color defaultBorder = Color.white;

    public VzRobot()
    {
        this(defaultFill, defaultBorder);
    }


    public VzRobot(Color fill)
    {
        this(fill, defaultBorder);
    }

    public VzRobot(Color fill, Color border)
    {

    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        assert(false);
    }

}