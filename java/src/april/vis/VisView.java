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

//    public Matrix projectionMatrix;
//    public Matrix modelMatrix;
    public int viewport[];

    public double perspective_fovy_degrees = 60;
    public double zclip_near = 0.01;
    public double zclip_far = 1000;

    GLU glu = new GLU();

    public VisView()
    {
    }

    public Matrix getProjectionMatrix()
    {
        int width = viewport[2] - viewport[0];
        int height = viewport[3] - viewport[1];

        double aspect = ((double) width) / height;
        double dist = LinAlg.distance(eye, lookAt);

        Matrix pM = VisUtil.gluPerspective(perspective_fovy_degrees, aspect, zclip_near, zclip_far);
        Matrix oM = VisUtil.glOrtho(-dist * aspect / 2, dist*aspect / 2, -dist/2, dist/2, -zclip_far, zclip_far);

        return pM.times(perspectiveness).plus(oM.times(1-perspectiveness));
    }

    public Matrix getModelViewMatrix()
    {
        return VisUtil.lookAt(eye, lookAt, up);
    }

    public GRay3D computeRay(double winx, double winy)
    {
        double ray_start[] = new double[3];
        double ray_end[] = new double[3];

        winy = viewport[3] - winy;

        double proj_matrix[] = getProjectionMatrix().getColumnPackedCopy();
        double model_matrix[] = getModelViewMatrix().getColumnPackedCopy();

        glu.gluUnProject(winx, winy, 0, model_matrix, 0, proj_matrix, 0, viewport, 0, ray_start, 0);

        glu.gluUnProject(winx, winy, 1, model_matrix, 0, proj_matrix, 0, viewport, 0, ray_end, 0);

        return new GRay3D(ray_start, LinAlg.subtract(ray_end, ray_start));
    }

    public double[] unprojectPoint(double winx, double winy, double winz)
    {
        double proj_matrix[] = getProjectionMatrix().getColumnPackedCopy();
        double model_matrix[] = getModelViewMatrix().getColumnPackedCopy();

        double xyz[] = new double[3];

        winy = viewport[3] - winy;

        glu.gluUnProject(winx, winy, winz, model_matrix, 0, proj_matrix, 0, viewport, 0,  xyz, 0);
        return xyz;
    }

    public double[] projectPoint(double x, double y, double z)
    {
        double result[] = { 0, 0, 0 };
        double proj_matrix[] = getProjectionMatrix().getColumnPackedCopy();
        double model_matrix[] = getModelViewMatrix().getColumnPackedCopy();

        glu.gluProject(x, y, z, model_matrix, 0, proj_matrix, 0, viewport, 0, result, 0);
        result[1] = viewport[3] - result[1];
        return result;
    }
}
