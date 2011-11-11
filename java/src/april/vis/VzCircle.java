package april.vis;

import java.awt.*;

public class VisCircle extends VisLines
{
    static VisVertexData vvd;


    public VisCircle(double r, Color fill,
                     Color border)
    {
        super(vvd, new VisConstantColor(border), 1, VisLines.TYPE.LINE_LOOP);

        assert(false);
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        assert(false);
    }

}