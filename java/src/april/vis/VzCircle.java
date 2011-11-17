package april.vis;

import java.awt.*;

public class VzCircle implements VisObject
{
    double r;
    Color lineColor;

    static float vd[] = makeCircleOutline(16);
    static long vdid = VisUtil.allocateID();

    public VzCircle(double r, Color lineColor)
    {
        this.r = r;
        this.lineColor = lineColor;
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        gl.glColor(lineColor);
        gl.gldBind(GL.VBO_TYPE_VERTEX, vdid, vd.length / 2, 2, vd);
        gl.glDrawArrays(GL.GL_LINE_LOOP, 0, vd.length / 2);
        gl.gldUnbind(GL.VBO_TYPE_VERTEX, vdid);
    }

    static float[] makeCircleOutline(int n)
    {
        float v[] = new float[n*2];

        for (int i = 0; i < n; i++) {
            double theta = 2*Math.PI * i / n;
            v[2*i+0] = (float) Math.cos(theta);
            v[2*i+1] = (float) Math.sin(theta);
        }

        return v;
    }

    static float[] makeCircleFill(int n)
    {
        float v[] = new float[(n+1)*2+2];

        v[0] = 0;
        v[1] = 0;

        for (int i = 0; i <= n; i++) {
            double theta = 2*Math.PI * i / n;
            v[2+2*i+0] = (float) Math.cos(theta);
            v[2+2*i+1] = (float) Math.sin(theta);
        }

        return v;
    }

}
