package april.vis;

import java.awt.*;
import java.util.*;
import april.jmat.*;

public class VzCylinder implements VisObject
{
    Style styles[];
    double r, h;
    int flags;

    public static final int TOP = 1, BOTTOM = 2;

    static VzMesh barrel;
    static VzMesh circle;

    static {
        VisVertexData barrelData = new VisVertexData();
        VisVertexData barrelNormals = new VisVertexData();
        VisVertexData circleData = new VisVertexData();

        circleData.add(new float[2]);

        int n = 16;
        for (int i = 0; i <= n; i++) {
            double theta = 2*Math.PI * i / n;

            barrelData.add(new float[] { (float) Math.cos(theta),
                                         (float) Math.sin(theta),
                                         1 });
            barrelData.add(new float[] { (float) Math.cos(theta),
                                         (float) Math.sin(theta),
                                         -1 });
            barrelNormals.add(new float[] { (float) Math.cos(theta),
                                            (float) Math.sin(theta),
                                            0 });
            barrelNormals.add(new float[] { (float) Math.cos(theta),
                                            (float) Math.sin(theta),
                                            0 });

            circleData.add(new float[] { (float) Math.cos(theta),
                                         (float) Math.sin(theta) });

        }

        barrel = new VzMesh(barrelData, barrelNormals, VzMesh.TRIANGLE_STRIP);
        circle = new VzMesh(circleData, VzMesh.TRIANGLE_FAN);
    }

    public VzCylinder(Style ... styles)
    {
        this(1, 1, styles);
    }

    public VzCylinder(double r, double h, Style ... styles)
    {
        this(r, h, TOP | BOTTOM, styles);
    }

    public VzCylinder(double r, double h, int flags, Style ... styles)
    {
        this.r = r;
        this.h = h;
        this.flags = flags;
        this.styles = styles;
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        gl.glPushMatrix();
        gl.glScaled(r, r, h);

        for (Style style : styles) {
            if (style instanceof VzMesh.Style) {
                barrel.render(vc, layer, rinfo, gl, (VzMesh.Style) style);

                if ((flags & TOP) != 0) {
                    gl.glNormal3f(0, 0, 1);
                    gl.glTranslated(0, 0, 1);
                    circle.render(vc, layer, rinfo, gl, (VzMesh.Style) style);
                    gl.glTranslated(0, 0, -1);
                }

                if ((flags & BOTTOM) != 0) {
                    gl.glNormal3f(0, 0, -1);
                    gl.glTranslated(0, 0, -1);
                    circle.render(vc, layer, rinfo, gl, (VzMesh.Style) style);
                    gl.glTranslated(0, 0, 1);
                }
            }
        }

        gl.glPopMatrix();
    }
}
