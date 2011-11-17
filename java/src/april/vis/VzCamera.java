package april.vis;

import java.awt.*;

import april.jmat.*;

/** A camera formed by a box and a pyramid, with a focal point (the
 * apex of the pyramid) pointing down the +x axis.
 **/
public class VzCamera implements VisObject
{
    static Color defaultFill = Color.gray;

    Color color;

    public VzCamera()
    {
        this(defaultFill);
    }

    public VzCamera(Color fill)
    {
        this.color = fill;
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        VisChain c = new VisChain(new VisChain(LinAlg.scale(1, .5, .5),
                                               LinAlg.translate(1, 0, 0),
                                               LinAlg.rotateY(-Math.PI/2),
                                               new VzSquarePyramid(new VisFillStyle(color), false)),
                                  new VzBox(new VisFillStyle(color)));

        c.render(vc, layer, rinfo, gl);
    }

}
