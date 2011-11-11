package april.vis;

import java.awt.*;

public class VzCircle extends VisLines
{
    static VisVertexData vvd;


    public VzCircle(double r, Color fill,
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