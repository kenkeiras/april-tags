package april.vis;

import april.jmat.*;

import java.awt.*;
import javax.media.opengl.*;
import javax.media.opengl.glu.*;
import java.util.*;

import april.jmat.geom.*;

/** Represents the view into a VisWorld, such as camera position. This
 * view should be considered immutable.
 **/
public class VisView
{
    public double lookAt[] = new double[] {0, 0, 0};
    public double eye[] = new double[] {0, 0, 10};
    public double up[] = new double[] {0, 1, 0};

    public double perspectiveness;

    public Matrix projectionMatrix;
    public Matrix modelMatrix;
    public int viewport[];

    GLU glu = new GLU();

    public VisView()
    {
    }

    /** Apply this view to the camera. **/
    public void setupCamera(GL gl, GLU glu)
    {
        gl.glGetIntegerv(gl.GL_VIEWPORT, viewport, 0);

        /////////// PROJECTION MATRIX ////////////////
        gl.glMatrixMode(gl.GL_PROJECTION);
        gl.glLoadIdentity();
        gl.glMultMatrixd(projectionMatrix.getColumnPackedCopy(), 0);

        /////////// MODEL MATRIX ////////////////
        gl.glMatrixMode(gl.GL_MODELVIEW);
        gl.glLoadIdentity();
        gl.glMultMatrixd(modelMatrix.getColumnPackedCopy(), 0);
    }

    public GRay3D computeRay(double winx, double winy)
    {
        double ray_start[] = new double[3];
        double ray_end[] = new double[3];

        winy = viewport[3] - winy;

        double proj_matrix[] = projectionMatrix.getColumnPackedCopy();
        double model_matrix[] = modelMatrix.getColumnPackedCopy();

        glu.gluUnProject(winx, winy, 0, model_matrix, 0, proj_matrix, 0, viewport, 0, ray_start, 0);

        glu.gluUnProject(winx, winy, 1, model_matrix, 0, proj_matrix, 0, viewport, 0, ray_end, 0);

        return new GRay3D(ray_start, LinAlg.subtract(ray_end, ray_start));
    }

    public double[] unprojectPoint(double winx, double winy, double winz)
    {
        double proj_matrix[] = projectionMatrix.getColumnPackedCopy();
        double model_matrix[] = modelMatrix.getColumnPackedCopy();

        double xyz[] = new double[3];

        winy = viewport[3] - winy;

        glu.gluUnProject(winx, winy, winz, model_matrix, 0, proj_matrix, 0, viewport, 0,  xyz, 0);
        return xyz;
    }

    public double[] projectPoint(double x, double y, double z)
    {
        double result[] = { 0, 0, 0 };
        double proj_matrix[] = projectionMatrix.getColumnPackedCopy();
        double model_matrix[] = modelMatrix.getColumnPackedCopy();
        glu.gluProject(x, y, z, model_matrix, 0, proj_matrix, 0, viewport, 0, result, 0);
        result[1] = viewport[3] - result[1];
        return result;
    }
}
