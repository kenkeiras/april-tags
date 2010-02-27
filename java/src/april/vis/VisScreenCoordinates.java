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

import april.jmat.geom.*;
import april.jmat.*;

/** VisObject wrapper that causes nested objects to be rendered
 * according to screen coordinates, (0,0) to (width,height).
 **/
public class VisScreenCoordinates implements VisObject
{
    VisObject vo;

    public VisScreenCoordinates(VisObject vo)
    {
        this.vo = vo;
    }

    public void render(VisContext vc, GL gl, GLU glu)
    {
        double model_matrix[] = new double[16];
        double proj_matrix[] = new double[16];
        int viewport[] = new int[4];

        VisUtil.pushGLWholeState(gl);

        gl.glGetDoublev(gl.GL_MODELVIEW_MATRIX, model_matrix, 0);
        gl.glGetDoublev(gl.GL_PROJECTION_MATRIX, proj_matrix, 0);
        gl.glGetIntegerv(gl.GL_VIEWPORT, viewport, 0);

    	// setup very dumb projection in pixel coordinates
        gl.glMatrixMode(gl.GL_PROJECTION);
        gl.glLoadIdentity();
        glu.gluOrtho2D(0,viewport[2],0,viewport[3]);

        gl.glMatrixMode(gl.GL_MODELVIEW);
        gl.glLoadIdentity();

        vo.render(vc, gl, glu);

        VisUtil.popGLWholeState(gl);
    }
}
