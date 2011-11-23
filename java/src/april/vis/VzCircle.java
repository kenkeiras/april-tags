package april.vis;

import java.awt.*;

public class VzCircle implements VisObject
{
    double r;

    Style styles[];

    public static final VzLines lines = new VzLines(makeCircleOutline(16), VzLines.LINE_LOOP);
    public static final VzMesh  mesh = new VzMesh(makeCircleFill(16), VzMesh.TRIANGLE_FAN);

    public VzCircle(Style ... styles)
    {
        this(1.0, styles);
    }


    public VzCircle(double r, Style ... styles)
    {
        this.r = r;
        this.styles = styles;
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl, Style style)
    {
        gl.glPushMatrix();
        gl.glScaled(r, r, r);

        if (style instanceof VzLines.Style)
            lines.render(vc, layer, rinfo, gl, (VzLines.Style) style);

        if (style instanceof VzMesh.Style)
            mesh.render(vc, layer, rinfo, gl, (VzMesh.Style) style);

        gl.glPopMatrix();
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        for (Style style : styles)
            render(vc, layer, rinfo, gl, style);
    }

    static VisVertexData makeCircleOutline(int n)
    {
        VisVertexData vd = new VisVertexData();

        for (int i = 0; i < n; i++) {
            double theta = 2*Math.PI * i / n;
            vd.add(new float[] { (float) Math.cos(theta),
                                 (float) Math.sin(theta) });
        }

        return vd;
    }

    static VisVertexData makeCircleFill(int n)
    {
        VisVertexData vd = new VisVertexData();

        vd.add(new float[] { 0, 0 });

        for (int i = 0; i <= n; i++) {
            double theta = 2*Math.PI * i / n;
            vd.add(new float[] { (float) Math.cos(theta),
                                 (float) Math.sin(theta) });
        }

        return vd;
    }
}
