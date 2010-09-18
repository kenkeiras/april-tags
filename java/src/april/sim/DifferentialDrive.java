package april.sim;

import java.util.*;

import april.util.*;
import april.jmat.*;
import april.lcmtypes.*;

public class DifferentialDrive
{
    public static final int HZ = 50;

    public Motor leftMotor = new Motor();
    public Motor rightMotor = new Motor();
    public double voltageScale = 12.0;

    // The true pose of the robot's rear axle (see centerOfRotation)
    public pose_t poseTruth = new pose_t();

    // A noise-corrupted version of poseTruth.
    public pose_t poseOdom = new pose_t();

    // The position of the robot's center of rotation with respect to
    // the rear axle.
    public double centerOfRotation[] = new double[] { 0.13, 0, 0 };

    // Used to implement collision detection. The robot is modeled as a sphere.
    public double centerOfCollisionSphere[] = new double[] { 0.14, 0, .2};
    public double collisionRadius = 0.38;

    // [left, right], where each motor is [-1,1]
    public double motorCommands[] = new double[2];

    public double wheelDiameter = 0.25; // diameter of wheels (m)
    public double baseline = 0.35; // distance between left and right wheels (m)

    SimWorld sw;
    HashSet<SimObject> ignore;

    /** ignore: a set of objects that will not be used for collision
     * detection. Typically, this includes the robot itself. **/
    public DifferentialDrive(SimWorld sw, HashSet<SimObject> ignore, double init_xyt[])
    {
        this.sw = sw;
        this.ignore = ignore;

        poseTruth.pos = new double[] { init_xyt[0], init_xyt[1], 0};
        poseTruth.orientation = LinAlg.rollPitchYawToQuat(new double[] {0, 0, init_xyt[2]});

        poseOdom = poseTruth.copy();

        new RunThread().start();
    }

    synchronized void update(double dt)
    {
        leftMotor.setVoltage(motorCommands[0]*voltageScale);
        rightMotor.setVoltage(motorCommands[1]*voltageScale);

        leftMotor.update(dt);
        rightMotor.update(dt);

        // temporarily make poseTruth point to center of
        // rotation. (we'll undo this at the end)
        double pos[] = LinAlg.add(poseTruth.pos, LinAlg.quatRotate(poseTruth.orientation, centerOfRotation));

        double left_rad_per_sec = leftMotor.getRadPerSec();
        double right_rad_per_sec = rightMotor.getRadPerSec();

        double dleft = dt * left_rad_per_sec * wheelDiameter;
        double dright = dt * right_rad_per_sec * wheelDiameter;

        double dl = (dleft + dright) / 2;
        double dtheta = (dright - dleft) / baseline;

        double dpos[] = LinAlg.quatRotate(poseTruth.orientation, new double[] { dl, 0, 0 });
        double dquat[] = LinAlg.rollPitchYawToQuat(new double[] {0, 0, dtheta});

        double newpos[] = LinAlg.add(pos, dpos);
        double neworient[] = LinAlg.quatMultiply(poseTruth.orientation, dquat);

        // go back to rear axle
        newpos = LinAlg.add(newpos, LinAlg.quatRotate(neworient, LinAlg.scale(centerOfRotation, -1)));

        // only accept movements that don't run into things
//        if (!world.isCollision(LinAlg.add(newpos, LinAlg.quatRotate(poseTruth.orientation, centerOfCollisionSphere)), collisionRadius)) {
        if (sw.collisionSphere(LinAlg.add(newpos, LinAlg.quatRotate(neworient, centerOfCollisionSphere)), ignore) > collisionRadius) {
            poseTruth.pos = newpos;
            poseTruth.orientation = neworient;

            // XXX: Implement some slippage.
            poseOdom.pos = LinAlg.copy(poseTruth.pos);
            poseOdom.orientation = LinAlg.copy(poseTruth.orientation);
        }

        poseTruth.utime = TimeUtil.utime();

        poseOdom.utime = poseTruth.utime;
    }

    class RunThread  extends Thread
    {
        public void run()
        {
            while (true) {
                update(1.0/HZ);

                TimeUtil.sleep(1000/HZ);
            }
        }
    }
}
