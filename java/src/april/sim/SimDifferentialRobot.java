package april.sim;

import april.lcmtypes.*;
import april.util.*;
import april.jmat.*;

import java.util.*;

import april.sim.collision.*;

/** Simulates a differentially driven robot. Note that robot masses
 * must be built into the motor objects.
 *
 * The robot tracks its position in an arbitrary coordinate system and
 * can provide "noisy" odometry estimates in addition to the robot's
 * estimates position.
 *
 * Basic collision checking can be handled by passing in an
 * CollisionTester object.
**/
public class SimDifferentialRobot
{
    SimMotor leftMotor, rightMotor;

    CollisionSet collisionSet;

    pose_t pose_truth = new pose_t();
    pose_t pose_odom = new pose_t();

    double odomNoiseTranslate, odomNoiseRotate;

    Random r = new Random();

    /**
     * odomNoise: simulate odometry noise by adding noise to
     * translations/rotations is a multiple of the actual motion. (The
     * product yields a standard deviation.)
     **/
    public SimDifferentialRobot(SimMotor leftMotor, SimMotor rightMotor, double radius, CollisionSet collisionSet,
                                double odomNoiseTranslate, double odomNoiseRotate)
    {
        this.leftMotor = leftMotor;
        this.rightMotor = rightMotor;

        this.collisionSet = collisionSet;

        this.odomNoiseTranslate = odomNoiseTranslate;
        this.odomNoiseRotate = odomNoiseRotate;

        new RunThread().start();
    }

    public void setVoltages(double leftVoltage, boolean leftConnect, double rightVoltage, boolean rightConnect)
    {
        leftMotor.setVoltage(leftVoltage, leftConnect);
        rightMotor.setVoltage(rightVoltage, rightConnect);
    }

    public void teleport(pose_t p)
    {
        this.pose_truth = p.copy();
        this.pose_odom = p.copy();
    }

    public pose_t getPoseTruth()
    {
        return pose_truth;
    }

    public pose_t getPoseOdom()
    {
        return pose_odom;
    }

    void update(double dt)
    {
        leftMotor.update(dt);
        rightMotor.update(dt);

        double left_rad_per_sec = leftMotor.getRadPerSec();
        double right_rad_per_sec = rightMotor.getRadPerSec();
        double wheel_diameter = 0.25;
        double baseline = 0.35;

        double dleft = dt * left_rad_per_sec * wheel_diameter;
        double dright = dt * right_rad_per_sec * wheel_diameter;

        double dl = (dleft + dright)/2;
        double dtheta = (dright - dleft) / baseline;

        double dpos[] = LinAlg.quatRotate(pose_truth.orientation, new double[] { dl, 0, 0 });
        double dquat[] = LinAlg.rollPitchYawToQuat(new double[] {0, 0, dtheta});

        double noisy_dpos[] = new double[] { r.nextGaussian()*dpos[0]*odomNoiseTranslate,
                                             r.nextGaussian()*dpos[1]*odomNoiseTranslate };

        double noisy_dquat[] = LinAlg.rollPitchYawToQuat(new double[] {0, 0, dtheta + r.nextGaussian()*dtheta*odomNoiseRotate});

        double newpos[] = LinAlg.add(pose_truth.pos, dpos);
        if (collisionSet != null && collisionSet.collisionPointDistance(new double[] { newpos[0], newpos[1], 0, 1 }) > 0) {
            pose_truth.pos = newpos;
            pose_truth.orientation = LinAlg.quatMultiply(pose_truth.orientation, dquat);

            pose_odom.pos = LinAlg.add(pose_odom.pos, noisy_dpos);
            pose_odom.orientation = LinAlg.quatMultiply(pose_odom.orientation, noisy_dquat);
        }

        pose_truth.utime = TimeUtil.utime();
        pose_odom.utime = pose_truth.utime;
    }

    class RunThread extends Thread
    {
        public void run()
        {
            while (true) {
                double dt = 0.05;
                update(dt);
                TimeUtil.sleep((int) (1000*dt));
            }
        }
    }
}
