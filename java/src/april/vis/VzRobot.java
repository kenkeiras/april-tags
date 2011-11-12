package april.vis;

import java.awt.*;

/** Renders a robot as a small triangle. **/
public class VzRobot implements VisObject
{
    final static Color defaultFill = Color.blue;
    final static Color defaultBorder = Color.white;

    final static float length = 0.6f;
    final static float width = .35f;

    final static VisVertexData vd = new VisVertexData(new float[] { -length/2, width/2 },
                                                      new float[] { length/2,  0 },
                                                      new float[] { -length/2, -width/2 });

    Color fillColor, borderColor;

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
        this.fillColor = fill;
        this.borderColor = border;
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        gl.glLineWidth(1f);

        vd.bind(gl);
        gl.glColor(fillColor);
        gl.glDrawArrays(GL.GL_LINE_LOOP, 0, vd.size());

        gl.glColor(borderColor);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, vd.size());

        vd.unbind(gl);
    }
}
