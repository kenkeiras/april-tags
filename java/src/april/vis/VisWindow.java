package april.vis;

import javax.media.opengl.*;
import javax.media.opengl.glu.*;
import java.awt.*;
import java.awt.image.*;
import java.nio.*;
import javax.swing.*;
import java.util.*;

import april.jmat.*;
import april.jmat.geom.*;

/** Performs a change of coordinates allowing rendering relative to
 * the corners of the screen. XXX should VisText be reimplemented in
 * terms of this?
 **/
public class VisWindow implements VisObject
{
    public enum ALIGN { TOP_LEFT, TOP, TOP_RIGHT, LEFT, CENTER, RIGHT, BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT, COORDINATES };

    public ALIGN align;                // where is the window?
    public double xy0[], xy1[];        // coordinates that will be mapped to our window (xy0: bottom left, xy1: upper right)
    public double winwidth, winheight; // size of the window, in pixels.

    ArrayList<VisObject> objects = new ArrayList<VisObject>();

    /** Define a new window of size (in pixels) winwidth x winheight,
        which will be aligned with respect to the viewable region of
        the screen. A coordinate transform will be applied so that the
        coordinate xy0 represents the lower left corner, and xy1
        represents the upper right corner. Then, all the objects will
        be drawn.
    **/
    public VisWindow(ALIGN align, double winwidth, double winheight, double xy0[], double xy1[], VisObject ... os)
    {
        this.align = align;
        this.winwidth = winwidth;
        this.winheight = winheight;
        this.xy0 = LinAlg.copy(xy0);
        this.xy1 = LinAlg.copy(xy1);

        for (VisObject o : os)
            add(o);
    }

    public void add(VisObject vo)
    {
        objects.add(vo);
    }

    public void clear()
    {
        objects.clear();
    }

    public void render(VisContext vc, GL gl, GLU glu)
    {
        // save original matrices
        VisUtil.pushGLWholeState(gl);

        int viewport[] = new int[4];
        gl.glGetIntegerv(gl.GL_VIEWPORT, viewport, 0);

        // compute the pixel coordinates of the window.
        double px0, px1, py0, py1;

        switch (align)
	    {
            case TOP_LEFT: case LEFT: case BOTTOM_LEFT:
                px0 = 0;
                px1 = winwidth;
                break;

            default: case TOP: case CENTER:	case BOTTOM:
                px0 = (viewport[0] + viewport[2])/2 - winwidth/2.0;
                px1 = px0 + winwidth;
                break;

            case TOP_RIGHT: case RIGHT: case BOTTOM_RIGHT:
                px1 = viewport[2]-1;
                px0 = px1 - winwidth;
                break;
	    }

        switch (align)
	    {
            case TOP_LEFT: case TOP: case TOP_RIGHT:
                // remember that y is inverted: y=0 is at bottom
                // left in GL
                py0 = viewport[3] - winheight - 1;
                py1 = py0 + winheight;
                break;

            default: case LEFT: case CENTER: case RIGHT:
                py0 = (viewport[1] + viewport[3])/2 - winheight/2.0;
                py1 = py0 + winheight;
                break;

            case BOTTOM_LEFT: case BOTTOM: case BOTTOM_RIGHT:
                py0 = 1;
                py1 = py0 + winheight;
                break;
	    }

        // setup very dumb projection in pixel coordinates
        gl.glMatrixMode(gl.GL_PROJECTION);
        gl.glLoadIdentity();
        glu.gluOrtho2D(0,viewport[2],0,viewport[3]);

        gl.glMatrixMode(gl.GL_MODELVIEW);
        gl.glLoadIdentity();

        // now, adjust the transforms...
        gl.glTranslated(px0, py0, 0);
        gl.glScaled(px1-px0, py1-py0, 0);

        // scale the input coordinates to [0, 1]
        gl.glScaled(1.0/(xy1[0]-xy0[0]), 1.0/(xy1[1]-xy0[1]), 0);
        gl.glTranslated(-xy0[0], -xy0[1], 0);

        // TODO: (optional?) Stenciling/ clip planes

        // everything will be drawn at z=0 due to our scaling, so
        // depth doesn't make sense.
        gl.glDisable(gl.GL_DEPTH_TEST);

        // render the objects
        for (VisObject o : objects) {

            VisUtil.pushGLState(gl);
            VisObject vo = (VisObject) o;
            vo.render(vc, gl, glu);
            VisUtil.popGLState(gl);
        }

        VisUtil.popGLWholeState(gl);
    }
}
