package april.viewer;

import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.imageio.*;
import java.util.*;

import april.vis.*;
import april.config.*;
import april.jmat.*;
import april.jmat.geom.*;
import april.util.*;

import lcm.lcm.*;
import april.lcmtypes.*;

/** VisCanvasEventAdapter that draws a robot and allows teleportation. **/
public class ViewRobot extends VisCanvasEventAdapter implements ViewObject, LCMSubscriber
{
    Viewer              viewer;
    String              name;
    Config              config;
    LCM                 lcm             = LCM.getSingleton();
    VisRobot            vrobot          = new VisRobot();
    VisObject           vavatar;
    java.util.Timer     leadTimer;
    LeadTask            leadTask        = new LeadTask();
    pose_t              pose;
    int                 followMode      = 0;
    String              followString[]  = new String[] { "Follow diabled", "Follow XY", "Follow XY+theta" };
    double              lastRobotPos[]  = new double[] { 0, 0, 0 };
    double              lastRobotQuat[] = new double[] { 1, 0, 0, 0 };
    ArrayList<double[]> trajectory      = new ArrayList<double[]>();
    boolean             enableTeleport;

    /** If interactive, teleports are enabled. **/
    public ViewRobot(Viewer viewer, String name, Config config)
    {
        this.viewer = viewer;
        this.name = name;
        this.config = config;
        viewer.getVisCanvas().addEventHandler(this, 0);
        vrobot.color = Color.cyan;

        enableTeleport = config.getBoolean("enable_teleport", false);

        if (true) {
            String path = config.getString("avatar.path", null);
            if (path != null) {
                try {
                    double T[][] = ConfigUtil.getRigidBodyTransform(config, "avatar");
                    double scale = config.getDouble("avatar.scale", 1);
                    T = LinAlg.multiplyMany(T, LinAlg.scale(scale, scale, scale));
                    RWX rwx = new RWX(path);
                    vavatar = new VisChain(T, rwx);
                } catch (IOException ex) {
                    System.out.println("Problem loading avatar model: "+ex);
                }
            }
            String cls = config.getString("avatar.visobject_class", null);
            if (cls != null) {
                try {
                    vavatar = (VisObject) ReflectUtil.createObject(cls);
                } catch (Exception ex) {
                    System.out.println("Problem loading avatar model: "+ex);
                }
            }
        }

        lcm.subscribe("POSE", this);
    }

    public void messageReceived(LCM lcm, String channel, LCMDataInputStream ins)
    {
        try {
            messageReceivedEx(channel, ins);
        } catch (IOException ex) {
            System.out.println("Exception: " + ex);
        }
    }

    void messageReceivedEx(String channel, LCMDataInputStream ins) throws IOException
    {
        if (channel.equals("POSE")) {

            this.pose = new pose_t(ins);
            double lastpos[] = trajectory.size() > 0 ? trajectory.get(trajectory.size() - 1) : null;
            if (lastpos == null || LinAlg.distance(lastpos, pose.pos) > 0.05) {
                trajectory.add(LinAlg.copy(pose.pos));
            }

            VisWorld.Buffer vb = viewer.getVisWorld().getBuffer("Robot");
            vb.addBuffered(new VisData(new VisDataLineStyle(Color.blue, 1), trajectory));
            vb.addBuffered(new VisChain(pose.orientation, pose.pos, vrobot));
            if (vavatar != null)
                vb.addBuffered(new VisChain(pose.orientation, pose.pos, vavatar));
            vb.switchBuffer();

            if (followMode > 0) {
                VisViewManager viewManager = viewer.getVisCanvas().getViewManager();
                viewManager.follow(lastRobotPos, lastRobotQuat, pose.pos, pose.orientation, followMode == 2);
            }

            lastRobotPos = LinAlg.copy(pose.pos);
            lastRobotQuat = LinAlg.copy(pose.orientation);
        }
    }

    public boolean mouseDragged(VisCanvas vc, GRay3D ray, MouseEvent e)
    {
        if (!vc.isPicking(this))
            return false;
        if (pose == null)
            return false;
        int mods = e.getModifiersEx();
        boolean shift = (mods & MouseEvent.SHIFT_DOWN_MASK) > 0;
        boolean ctrl = (mods & MouseEvent.CTRL_DOWN_MASK) > 0;
        boolean alt = shift & ctrl;
        ctrl = ctrl & (!alt);
        shift = shift & (!alt);
        // only left mouse button.
        if ((mods & InputEvent.BUTTON1_DOWN_MASK) == 0)
            return false;

        if (!shift && !ctrl) {
            // translate. Move robot to the mouse position
            double pos[] = ray.intersectPlaneXY(pose.pos[2]);
            pose.pos = pos;
        }

        if (shift) {
            // rotate. Point the robot towards the mouse position
            double pos[] = ray.intersectPlaneXY(pose.pos[2]);
            double theta = Math.atan2(pos[1] - pose.pos[1], pos[0] - pose.pos[0]);
            pose.orientation = LinAlg.angleAxisToQuat(theta, new double[] { 0, 0, 1 });
        }

        if (ctrl) {
            if (leadTimer == null) {
                leadTask = new LeadTask();
                leadTask.ray = ray;
                leadTimer = new java.util.Timer();
                leadTimer.schedule(leadTask, 0, (int) (1000.0 / LeadTask.HZ));
            }
            leadTask.ray = ray;
            return true;
        }
        if (leadTimer != null)
        {
            leadTimer.cancel();
            leadTimer = null;
        }
        lcm.publish("POSE_TELEPORT", pose);
        return true;
    }

    class LeadTask extends TimerTask
    {
        GRay3D              ray;
        static final double MAX_VELOCITY         = 0.5;     // m/s
        static final double MAX_ANGULAR_VELOCITY = Math.PI; // rad/s
        static final int    HZ                   = 40;
        static final double DT                   = 1.0 / HZ;

        public void run()
        {
            // lead the robot.
            double pos[] = ray.intersectPlaneXY(pose.pos[2]);
            double distance = LinAlg.distance(pos, pose.pos);
            if (distance < 0.3)
                return;

            double theta = Math.atan2(pos[1] - pose.pos[1], pos[0] - pose.pos[0]);
            pose.orientation = LinAlg.angleAxisToQuat(theta, new double[] { 0, 0, 1 });
            double err[] = LinAlg.subtract(pos, pose.pos);
            LinAlg.scale(err, .1); // P control, sort of.
            pose.pos = LinAlg.add(pose.pos, err);
            lcm.publish("POSE_TELEPORT", pose);
        }
    }

    public boolean mousePressed(VisCanvas vc, GRay3D ray, MouseEvent e)
    {
        return false;
    }

    public boolean mouseReleased(VisCanvas vc, GRay3D ray, MouseEvent e)
    {
        if (!vc.isPicking(this))
            return false;

        if (leadTimer != null) {
            leadTimer.cancel();
            leadTimer = null;
        }

        vc.releasePick();
        return true;
    }

    public double pickQuery(VisCanvas vc, GRay3D ray)
    {
        return queryDistance(vc, ray);
    }

    public void pickNotify(boolean winner)
    {
        vrobot.setSelectedState(winner ? 2 : 0);
    }

    public double hoverQuery(VisCanvas vc, GRay3D ray)
    {
        return queryDistance(vc, ray);
    }

    public void hoverNotify(boolean winner)
    {
        vrobot.setSelectedState(winner ? 1 : 0);
    }

    protected double queryDistance(VisCanvas vc, GRay3D ray)
    {
        if (pose == null)
            return -1;
        if (!enableTeleport)
            return -1;

        double pos[] = ray.intersectPlaneXY();
        double dist = Math.sqrt(LinAlg.sq(pos[0] - pose.pos[0]) + LinAlg.sq(pos[1] - pose.pos[1]));
        return dist < 1 ? dist : -1;
    }

    public String getName()
    {
        return "Robot Position";
    }

    VisObject lastFollowTemporary;
    int       findCount;
    boolean   lastCharT = false;

    public boolean keyTyped(VisCanvas vc, KeyEvent e)
    {
        if (e.getKeyChar() == 'f')
        {
            // Follow mode
            followMode = (followMode + 1) % 3;
            vc.getWorld().removeTemporary(lastFollowTemporary);
            lastFollowTemporary = new VisText(VisText.ANCHOR.CENTER, followString[followMode]);
            vc.getWorld().addTemporary(lastFollowTemporary, 1.0);
            return true;
        }
        if (e.getKeyChar() == 'F' && lastRobotPos != null)
        {
            findCount++;
            if (findCount % 2 == 1)
            {
                // FIND.
                VisViewManager viewManager = vc.getViewManager();
                viewManager.viewGoal.lookAt(LinAlg.add(lastRobotPos, new double[] { 0, 0, 10 }),
                                            lastRobotPos,
                                            new double[] { 0, 1, 0 });
            }
            else
            {
                VisViewManager viewManager = vc.getViewManager();
                double rpy[] = LinAlg.quatToRollPitchYaw(lastRobotQuat);
                double behindDist = 5;
                viewManager.viewGoal.lookAt(LinAlg.add(lastRobotPos,
                                                       new double[] { Math.cos(-rpy[2]) * behindDist,
                                                                      Math.sin(-rpy[2]) * behindDist,
                                                                      2 }),
                                            lastRobotPos,
                                            new double[] { 0, 0, 1 });
            }
            return true;
        }
        if (lastCharT)
        {
            if (e.getKeyChar() == 'c')
            {
                trajectory.clear();
            }
        }
        lastCharT = e.getKeyChar() == 't';
        return false;
    }

    public void doHelp(HelpOutput houts)
    {
        houts.beginMouseCommands(this);
        houts.addMouseCommand(this, HelpOutput.LEFT | HelpOutput.DRAG, "Teleport (translate)");
        houts.addMouseCommand(this, HelpOutput.LEFT | HelpOutput.DRAG | HelpOutput.SHIFT, "Teleport (rotate)");
        houts.beginKeyboardCommands(this);
        houts.addKeyboardCommand(this, "f", HelpOutput.CTRL, "Cycle through follow modes");
        houts.addKeyboardCommand(this, "F", HelpOutput.CTRL, "Find robot");
    }
}
