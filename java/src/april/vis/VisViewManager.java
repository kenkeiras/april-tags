package april.vis;

import april.jmat.*;

import java.awt.*;
import javax.media.opengl.*;
import javax.media.opengl.glu.*;
import java.util.*;

import april.jmat.geom.*;

/** Represents the view into a VisWorld, such as camera position. **/
public class VisViewManager
{
    public double perspective_vertical_fov_degrees;

    // 1.0 for full perspective, 0.0 for orthographic. Interpolated otherwise.
    public double perspectiveness = 1.0;

    public double lookAtGoal[] = new double[] {0, 0, 0};
    public double eyeGoal[]    = new double[] {0, 0, 10};
    public double upGoal[]     = new double[] {0, 1, 0};

    public double manipulationPoint[] = new double[] {0,0,0,1};

    public double interfaceMode = 2.5;

    double followPos[] = new double[3];
    double followQuat[] = new double[] {1, 0, 0, 0};

    HashMap<String, Boolean> enabledBuffers = new HashMap<String, Boolean>();

    ArrayList<VisViewListener> listeners = new ArrayList<VisViewListener>();

    VisContext vc;

    public VisViewManager(VisContext vc)
    {
        this.vc = vc;

        perspectiveness = 1.0;
        perspective_vertical_fov_degrees = 60;
    }

    public void addListener(VisViewListener vvl)
    {
        listeners.add(vvl);
    }

    public void removeListener(VisViewListener vvl)
    {
        listeners.remove(vvl);
    }

    public double getInterfaceMode()
    {
        return interfaceMode;
    }

    public void setInterfaceMode(double d)
    {
        interfaceMode = d;
        adjustForInterfaceMode();

        for (VisViewListener vvl: listeners)
            vvl.viewCameraChanged(vc);
        vc.draw();
    }

    public void setBufferEnabled(String buffer, boolean b)
    {
        enabledBuffers.put(buffer, b);

        for (VisViewListener vvl : listeners)
            vvl.viewBufferEnabledChanged(vc, buffer, b);
        vc.draw();
    }

    public boolean isBufferEnabled(String buffer)
    {
        Boolean b = enabledBuffers.get(buffer);

        return (b == null || b == true);
    }

    public VisView getView(int viewport[])
    {
        VisView vv = new VisView();

        vv.viewport = LinAlg.copy(viewport);
        vv.eye = LinAlg.copy(eyeGoal);
        vv.lookAt = LinAlg.copy(lookAtGoal);
        vv.up = LinAlg.copy(upGoal);

        return vv;
    }

    // certain camera positions are forbidden in 2.5d camera modes. This enforces the constraint.
    void adjustForInterfaceMode()
    {
        if (interfaceMode == 2.0) {
            eyeGoal[0] = lookAtGoal[0];
            eyeGoal[1] = lookAtGoal[1];
            eyeGoal[2] = Math.abs(eyeGoal[2]);
            upGoal[2] = 0;
            if (LinAlg.magnitude(upGoal) < 1E-10)
                upGoal = new double[] {0, 1, 0};
            else
                upGoal = LinAlg.normalize(upGoal);
            lookAtGoal[2] = 0;
        } else if (interfaceMode == 2.5) {
            double p2eye[] = LinAlg.normalize(LinAlg.subtract(eyeGoal, lookAtGoal));
            double bad[] = LinAlg.crossProduct(new double[] {0,0,1}, p2eye);
            double dot = LinAlg.dotProduct(bad, upGoal);
            upGoal = LinAlg.subtract(upGoal, LinAlg.scale(bad, dot));
            //	    lookAtGoal[2] = 0;
        }

        upGoal = LinAlg.normalize(upGoal);
    }

    public void lookAt(double eye[], double lookAt[], double up[])
    {
        this.eyeGoal = LinAlg.copy(eye);
        this.lookAtGoal = LinAlg.copy(lookAt);
        this.upGoal = LinAlg.copy(up);

        adjustForInterfaceMode();

        for (VisViewListener vvl: listeners)
            vvl.viewCameraChanged(vc);
        vc.draw();
    }

    /** Set ortho projection that contains the rectangle whose corners are specified. **/
    public void fit2D(double xy0[], double xy1[])
    {
        this.perspectiveness = 0;
        this.lookAtGoal = new double[] {(xy0[0]+xy1[0])/2.0,
                                        (xy0[1]+xy1[1])/2.0,
                                        0};
        this.upGoal = new double[] {0, 1, 0};
        // XXX: Approximate
        double dist = Math.sqrt(Math.pow(xy0[0]-xy1[0],2) + Math.pow(xy0[1]-xy1[1],2));
        this.eyeGoal = new double[] {lookAtGoal[0], lookAtGoal[1], dist};

        for (VisViewListener vvl: listeners)
            vvl.viewCameraChanged(vc);
        vc.draw();
    }

    double follow_lastpos[], follow_lastquat[];
    public void follow(double pos[], double quat[], boolean followYaw)
    {
        if (follow_lastpos != null) {
            follow(follow_lastpos, follow_lastquat, pos, quat, followYaw);
        }

        follow_lastpos = LinAlg.copy(pos);
        follow_lastquat = LinAlg.copy(quat);
    }

    public void follow(double lastPos[], double lastQuat[], double newPos[], double newQuat[],
                       boolean followYaw)
    {
        if (followYaw) {
            // follow X,Y, and orientation.

            // our strategy is to compute the eye,lookAt relative to
            // the vehicle position, then use the new vehicle position
            // to recompute new eye/lookAt. We'll keep 'up' as it is.

            double v2eye[] = LinAlg.subtract(lastPos, eyeGoal);
            double v2look[] = LinAlg.subtract(lastPos, lookAtGoal);

            // this is the vector that the robot is newly pointing in
            double vxy[] = new double[] { 1, 0, 0};
            vxy = LinAlg.quatRotate(newQuat, vxy);
            vxy[2] = 0;

            // where was the car pointing last time?
            double theta = LinAlg.quatToRollPitchYaw(newQuat)[2] -
                LinAlg.quatToRollPitchYaw(lastQuat)[2];

            double zaxis[] = new double[] {0,0,1};
            double q[] = LinAlg.angleAxisToQuat(theta, zaxis);

            v2look = LinAlg.quatRotate(q, v2look);
            double newLookAt[] = LinAlg.subtract(newPos, v2look);

            v2eye = LinAlg.quatRotate(q, v2eye);
            double newEye[] = LinAlg.subtract(newPos, v2eye);
            double newUp[] = LinAlg.quatRotate(q, upGoal);

            lookAt(newEye, newLookAt, newUp);
        } else {
            // follow in X/Y (but not yaw)

            double dpos[] = LinAlg.subtract(newPos, lastPos);

            double newEye[] = LinAlg.add(eyeGoal, dpos);
            double newLookAt[] = LinAlg.add(lookAtGoal, dpos);

            lookAt(newEye, newLookAt, upGoal);
        }
    }

    // rotate field of view, preserving current lookAt
    public void rotate(double q[])
    {
        double toEyeVec[] = LinAlg.subtract(eyeGoal, lookAtGoal);
        double newToEyeVec[] = LinAlg.quatRotate(q, toEyeVec);
        double neweye[] = LinAlg.add(lookAtGoal, newToEyeVec);
        double newup[] = LinAlg.quatRotate(q, upGoal);

        lookAt(neweye, lookAtGoal, newup);
    }

    public void rotate(double angle, double axis[])
    {
        rotate(LinAlg.angleAxisToQuat(angle, axis));
    }

    public void setPerspectiveness(double perspectiveness)
    {
        this.perspectiveness = perspectiveness;

        for (VisViewListener vvl: listeners)
            vvl.viewCameraChanged(vc);
        vc.draw();
    }

    public void setManipulationPoint(double xyz[])
    {
        this.manipulationPoint = LinAlg.copy(xyz);
    }
}
