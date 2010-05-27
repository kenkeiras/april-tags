package april.vis;

import java.awt.*;
import java.util.*;
import java.nio.*;
import javax.media.opengl.*;
import javax.media.opengl.glu.*;
import com.sun.opengl.util.*;

import april.jmat.geom.*;

/** Render VisData as individual points. **/
public class VisDataPointStyle implements VisDataStyle
{
    Colorizer colorizer;
    Color c;
    float size;

    public VisDataPointStyle(Colorizer colorizer, double size)
    {
        this.colorizer = colorizer;
        this.size = (float) size;
    }

    public VisDataPointStyle(Color c, double size)
    {
        this.c = c;
        this.size = (float) size;
    }

    public void renderStyle(VisContext vc, GL gl, GLU glu, VisData vdata)
    {
        ArrayList<double[]> points = vdata.points;

        if (c != null)
            VisUtil.setColor(gl, c);

        gl.glPointSize(size);
        gl.glDisable(GL.GL_LIGHTING);

        DoubleBuffer vertexbuf = vdata.getVertexBuffer();
        vertexbuf.rewind();

        IntBuffer colorbuf = null;

        gl.glEnableClientState(GL.GL_VERTEX_ARRAY);

        if (colorizer != null) {
            colorbuf = BufferUtil.newIntBuffer(points.size());
            for (int pidx = 0; pidx < points.size(); pidx++) {
                colorbuf.put(colorizer.colorize(points.get(pidx)));
            }
            colorbuf.rewind();

            gl.glEnableClientState(GL.GL_COLOR_ARRAY);
            gl.glColorPointer(4, GL.GL_UNSIGNED_BYTE, 0, colorbuf);
        }

        gl.glVertexPointer(3, GL.GL_DOUBLE, 0, vertexbuf);
        gl.glDrawArrays(GL.GL_POINTS, 0, points.size());
        gl.glDisableClientState(GL.GL_VERTEX_ARRAY);

        if (colorbuf != null)
            gl.glDisableClientState(GL.GL_COLOR_ARRAY);

        gl.glEnable(GL.GL_LIGHTING);
    }
}
