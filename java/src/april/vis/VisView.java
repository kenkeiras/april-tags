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

    public double perspectiveness = 1.0;

    public int viewport[] = new int[] {0, 0, 100, 100};

    public double perspective_fovy_degrees = 50;
    public double zclip_near = 0.01;
    public double zclip_far = 1000;

    static GLU glu = new GLU();

    public VisView()
    {
    }

    public VisView copy()
    {
        VisView vv = new VisView();
        vv.viewport = LinAlg.copy(viewport);
        vv.eye = LinAlg.copy(eye);
        vv.lookAt = LinAlg.copy(lookAt);
        vv.up = LinAlg.copy(up);

        vv.perspectiveness = perspectiveness;
        vv.perspective_fovy_degrees = perspective_fovy_degrees;
        vv.zclip_near = zclip_near;
        vv.zclip_far = zclip_far;

        return vv;
    }

    public void lookAt(double eye[], double lookAt[], double up[])
    {
        this.eye = LinAlg.copy(eye);
        this.lookAt = LinAlg.copy(lookAt);
        this.up = LinAlg.copy(up);
    }

    /** Set ortho projection that contains the rectangle whose corners are specified. **/
    public void fit2D(double xy0[], double xy1[])
    {
        this.perspectiveness = 0;
        lookAt = new double[] {(xy0[0]+xy1[0])/2.0,
                                             (xy0[1]+xy1[1])/2.0,
                                             0};
        up = new double[] {0, 1, 0};

        // XXX: Approximate
        double dist = Math.sqrt(Math.pow(xy0[0]-xy1[0],2) + Math.pow(xy0[1]-xy1[1],2));
        eye = new double[] {lookAt[0], lookAt[1], dist};
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

    void adjustForInterfaceMode(double interfaceMode)
    {
        if (interfaceMode == 2.0) {
            eye[0] = lookAt[0];
            eye[1] = lookAt[1];
            eye[2] = Math.abs(eye[2]);
            up[2] = 0;
            if (LinAlg.magnitude(up) < 1E-10)
                up = new double[] {0, 1, 0};
            else
                up = LinAlg.normalize(up);
            lookAt[2] = 0;
        } else if (interfaceMode == 2.5) {
            double p2eye[] = LinAlg.normalize(LinAlg.subtract(eye, lookAt));
            double bad[] = LinAlg.crossProduct(new double[] {0,0,1}, p2eye);
            double dot = LinAlg.dotProduct(bad, up);
            up = LinAlg.subtract(up, LinAlg.scale(bad, dot));
        }

        up = LinAlg.normalize(up);
    }

    // rotate field of view, preserving current lookAt
    public void rotate(double q[])
    {
        double toEyeVec[] = LinAlg.subtract(eye, lookAt);
        double newToEyeVec[] = LinAlg.quatRotate(q, toEyeVec);
        double neweye[] = LinAlg.add(lookAt, newToEyeVec);
        double newup[] = LinAlg.quatRotate(q, up);

        lookAt(neweye, lookAt, newup);
    }

    public void rotate(double angle, double axis[])
    {
        rotate(LinAlg.angleAxisToQuat(angle, axis));
    }
}
