package april.vis;

import java.awt.*;

public class VzBox implements VisObject
{
    // vertex data
    static float vd[] = new float[] { -1, 1, -1,
                                      1, 1, -1,
                                      1, -1, -1,
                                      -1, -1, -1,

                                      -1, -1, -1,
                                      1, -1, -1,
                                      1, -1, 1,
                                      -1, -1, 1,

                                      -1, -1, 1,
                                      -1, 1, 1,
                                      -1, 1, -1,
                                      -1, -1, -1,

                                      1, 1, -1,
                                      -1, 1, -1,
                                      -1, 1, 1,
                                      1, 1, 1,

                                      -1, -1, 1,
                                      1, -1, 1,
                                      1, 1, 1,
                                      -1, 1, 1 };

    static float nd[] = new float[] { 0, 0, -1,
                                      0, 0, -1,
                                      0, 0, -1,
                                      0, 0, -1,

                                      0, 1, 0,
                                      0, 1, 0,
                                      0, 1, 0,
                                      0, 1, 0,

                                      1, 0, 0,
                                      1, 0, 0,
                                      1, 0, 0,
                                      1, 0, 0,

                                      1, 0, 0,
                                      1, 0, 0,
                                      1, 0, 0,
                                      1, 0, 0,

                                      0, 1, 0,
                                      0, 1, 0,
                                      0, 1, 0,
                                      0, 1, 0,

                                      0, 0, 1,
                                      0, 0, 1,
                                      0, 0, 1,
                                      0, 0, 1
    };




    public VzBox(double sx, double sy, double sz,
                 Color fill)
    {
        this(sx,sy,sz,fill, null);
    }

    public VzBox(double sx, double sy, double sz,
                 Color fill, Color border)
    {

    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        assert(false);
    }

}
