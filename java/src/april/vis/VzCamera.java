package april.vis;

import java.awt.*;

import april.jmat.*;

/** A camera formed by a box and a pyramid, with a focal point (the
 * apex of the pyramid) pointing down the +x axis.
 **/
public class VzCamera implements VisObject
{
    Style styles[];
    VzBox box = new VzBox();
    VzSquarePyramid pyramid = new VzSquarePyramid();

    public VzCamera()
    {
        this(new VzMesh.Style(Color.gray));
    }

    public VzCamera(Style ... styles)
    {
        this.styles = styles;
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl, Style style)
    {
        box.render(vc, layer, rinfo, gl, style);

        gl.glPushMatrix();
        gl.glMultMatrix(LinAlg.multiplyMany(LinAlg.scale(1, .5, .5),
                                            LinAlg.translate(1, 0, 0),
                                            LinAlg.rotateY(-Math.PI/2)));

        pyramid.render(vc, layer, rinfo, gl, style);

        gl.glPopMatrix();
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        for (Style style : styles) {
            render(vc, layer, rinfo, gl, style);
        }
    }

}
