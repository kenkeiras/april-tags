package april.vis;

import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.nio.channels.*;
import java.nio.*;

import javax.media.opengl.*;
import javax.media.opengl.glu.*;
import javax.swing.*;

import com.sun.opengl.util.*;

import april.jmat.*;
import april.jmat.geom.*;

/** Useful Vis-related utilities. **/
public final class VisUtil
{
    public static final Color hoverColor = new Color(0, 200, 60);
    public static final Color pickColor = new Color(255, 255, 0);

    public static void setColor(GL gl, int aarrggbb)
    {
        gl.glColor4f(((aarrggbb >> 16)&0xff)/255f,
                     ((aarrggbb >> 8)&0xff)/255f,
                     ((aarrggbb) & 0xff)/255f,
                     ((aarrggbb >> 24)&0xff)/255f);
    }

    public static void setColor(GL gl, Color c)
    {
        gl.glColor4f(c.getRed()/255f,
                     c.getGreen()/255f,
                     c.getBlue()/255f,
                     c.getAlpha()/255f);
    }

    public static void multiplyMatrix(GL gl, Matrix m)
    {
        assert(m.getColumnDimension()==4);
        assert(m.getRowDimension()==4);

        gl.glMultMatrixd(m.getColumnPackedCopy(), 0);
    }

    public static void pushGLState(GL gl)
    {
        // Horrendous performance penalty here!
        //	gl.glPushAttrib(gl.GL_ENABLE_BIT | gl.GL_POINT_BIT | gl.GL_POLYGON_STIPPLE_BIT |
        //			gl.GL_POLYGON_BIT | gl.GL_LINE_BIT | gl.GL_FOG_BIT | gl.GL_LIGHTING_BIT);
        gl.glPushMatrix();
    }

    public static void pushGLWholeState(GL gl)
    {
        gl.glPushAttrib(gl.GL_ENABLE_BIT | gl.GL_POINT_BIT | gl.GL_POLYGON_STIPPLE_BIT |
                        gl.GL_POLYGON_BIT | gl.GL_LINE_BIT | gl.GL_FOG_BIT | gl.GL_LIGHTING_BIT);

        gl.glMatrixMode(gl.GL_PROJECTION);
        gl.glPushMatrix();
        gl.glMatrixMode(gl.GL_MODELVIEW);
        gl.glPushMatrix();
    }

    public static void popGLWholeState(GL gl)
    {
        gl.glMatrixMode(gl.GL_MODELVIEW);
        gl.glPopMatrix();
        gl.glMatrixMode(gl.GL_PROJECTION);
        gl.glPopMatrix();
        gl.glMatrixMode(gl.GL_MODELVIEW);
        gl.glPopAttrib();
    }

    public static void popGLState(GL gl)
    {
        gl.glPopMatrix();
        //	gl.glPopAttrib();
    }

    public static Matrix gluPerspective(double fovy, double aspect, double znear, double zfar)
    {
        Matrix M = new Matrix(4,4);

        double f = 1.0 / Math.tan(Math.toRadians(fovy)/2);

        M.set(0,0, f/aspect);
        M.set(1,1, f);
        M.set(2,2, (zfar+znear)/(znear-zfar));
        M.set(2,3, 2*zfar*znear / (znear-zfar));
        M.set(3,2, -1);

        return M;
    }

    public static Matrix glOrtho(double left, double right, double bottom, double top, double znear, double zfar)
    {
        Matrix M = new Matrix(4,4);

        M.set(0,0, 2 / (right - left));
        M.set(0,3, -(right+left)/(right-left));
        M.set(1,1, 2 / (top-bottom));
        M.set(1,3, -(top+bottom)/(top-bottom));
        M.set(2,2, -2 / (zfar - znear));
        M.set(2,3, -(zfar+znear)/(zfar-znear));
        M.set(3,3, 1);

        return M;
    }

    public static Matrix lookAt(double eye[], double c[], double up[])
    {
        up = LinAlg.normalize(up);
        double f[] = LinAlg.normalize(LinAlg.subtract(c, eye));

        double s[] = LinAlg.crossProduct(f, up);
        double u[] = LinAlg.crossProduct(s, f);

        Matrix M = Matrix.identity(4,4);
        M.set(0,0, s[0]);
        M.set(0,1, s[1]);
        M.set(0,2, s[2]);
        M.set(1,0, u[0]);
        M.set(1,1, u[1]);
        M.set(1,2, u[2]);
        M.set(2,0, -f[0]);
        M.set(2,1, -f[1]);
        M.set(2,2, -f[2]);

        Matrix T = Matrix.identity(4,4);
        T.set(0,3, -eye[0]);
        T.set(1,3, -eye[1]);
        T.set(2,3, -eye[2]);

        M=M.times(T);
        return M;
    }

    static final boolean getProperty(String name, boolean def)
    {
        String s = System.getProperty(name);
        if (s==null)
            return def;

        s = s.trim().toLowerCase();
        if (s.equals("0") || s.equals("false") || s.equals("no"))
            return false;
        if (s.equals("1") || s.equals("true") || s.equals("yes"))
            return true;
        System.out.println(name+": Bad value "+s);
        return def;
    }

    static final int getProperty(String name, int def)
    {
        String s = System.getProperty(name);
        if (s==null)
            return def;

        try {
            return Integer.parseInt(s);
        } catch (Exception ex) {
        }

        System.out.println(name+": Bad value "+s);
        return def;
    }

    // vertically flip image
    public static void flipImage(int stride, int height, byte b[])
    {
        byte tmp[] = new byte[stride];

        for (int row = 0; row < (height-1)/2; row++) {

            int rowa = row;
            int rowb = height-1 - rowa;

            // swap rowa and rowb

            // tmp <-- rowa
            System.arraycopy(b, rowa*stride, tmp, 0, stride);

            // rowa <-- rowb
            System.arraycopy(b, rowb*stride, b, rowa*stride, stride);

            // rowb <-- tmp
            System.arraycopy(tmp, 0, b, rowb*stride, stride);
        }
    }

    // vertically flip image
    public static void flipImage(int stride, int height, float b[])
    {
        float tmp[] = new float[stride];

        for (int row = 0; row < (height-1)/2; row++) {

            int rowa = row;
            int rowb = height-1 - rowa;

            // swap rowa and rowb

            // tmp <-- rowa
            System.arraycopy(b, rowa*stride, tmp, 0, stride);

            // rowa <-- rowb
            System.arraycopy(b, rowb*stride, b, rowa*stride, stride);

            // rowb <-- tmp
            System.arraycopy(tmp, 0, b, rowb*stride, stride);
        }
    }
}
