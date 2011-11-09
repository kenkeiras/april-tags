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
import java.net.*;
import java.nio.*;

import april.config.*;
import april.util.*;
import april.velodyne.*;

import lcm.lcm.*;
import april.lcmtypes.*;
import april.jmat.*;
import april.vis.*;


/** Views velodyne data. **/
public class ViewVelodyne implements ViewObject, LCMSubscriber
{
    Viewer                         viewer;
    String                         name;
    Config                         config;
    LCM                            lcm        = LCM.getSingleton();
    PoseTracker                    pt         = PoseTracker.getSingleton();
    String                         channel;
    double                         spos[], squat[];
    VelodyneCalibration            calib      = VelodyneCalibration.makeMITCalibration();
    int                            lastbucket = 0;
    ArrayList<ArrayList<double[]>> points;

    public ViewVelodyne(Viewer viewer, String name, Config config)
    {
        this.viewer = viewer;
        this.name = name;
        this.config = config;
        this.channel = config.getString("channel", "VELODYNE");
        // sensor position in robot frame
        this.spos = ConfigUtil.getPosition(config.getRoot(), channel);
        this.squat = ConfigUtil.getQuaternion(config.getRoot(), channel);
        points = new ArrayList<ArrayList<double[]>>();
        for (int i = 0; i < 360; i++)
            points.add(new ArrayList<double[]>());
        lcm.subscribe("VELODYNE", this);
    }

    public void messageReceived(LCM lcm, String channel, LCMDataInputStream ins)
    {
        try
        {
            messageReceivedEx(channel, ins);
        } catch (IOException ex)
        {
            System.out.println("Exception: " + ex);
        }
    }

    void messageReceivedEx(String channel, LCMDataInputStream ins) throws IOException
    {
        if (channel.equals(this.channel))
        {
            velodyne_t vdata = new velodyne_t(ins);
            pose_t pose = pt.get(vdata.utime);
            if (pose == null)
                return;
            VisWorld.Buffer vb = viewer.getVisWorld().getBuffer(this.channel);
            Velodyne v = new Velodyne(calib, vdata.data);
            Velodyne.Sample vs = new Velodyne.Sample();
            double B2G[][] = LinAlg.quatPosToMatrix(pose.orientation, pose.pos);
            double S2B[][] = LinAlg.quatPosToMatrix(squat, spos);
            double T[][] = LinAlg.matrixAB(B2G, S2B);
            synchronized (this)
            {
                while (v.next(vs))
                {
                    // System.out.printf("%15f\n", vs.ctheta);
                    int bucket = (int) (vs.ctheta * (360 / (2 * Math.PI)));
                    if (bucket != lastbucket)
                    {
                        points.get(bucket).clear();
                        lastbucket = bucket;
                    }
                    points.get(bucket).add(LinAlg.transform(T, vs.xyz));
                }
            }
            vb.addBack(new MyVisObject());
            vb.swap();
        }
    }

    class MyVisObject implements VisObject
    {
        ColorMapper cm = ColorMapper.makeJetWhite(-1, +3);

        public void render(VisContext vc, GL gl, GLU glu)
        {
            synchronized (ViewVelodyne.this)
            {
                if (false)
                {
                    // This code seems to leak memory, but should be
                    // faster than the default code.
                    gl.glPointSize(2);
                    gl.glEnableClientState(GL.GL_VERTEX_ARRAY);
                    gl.glEnableClientState(GL.GL_COLOR_ARRAY);
                    for (ArrayList<double[]> ps : points)
                    {
                        if (ps.size() == 0)
                            continue;
                        DoubleBuffer vertbuf = BufferUtil.newDoubleBuffer(3 * ps.size());
                        DoubleBuffer colorbuf = BufferUtil.newDoubleBuffer(3 * ps.size());
                        for (int i = 0; i < ps.size(); i++)
                        {
                            double p[] = ps.get(i);
                            vertbuf.put(p[0]);
                            vertbuf.put(p[1]);
                            vertbuf.put(p[2]);
                            int c = cm.map(p[2] - pt.get().pos[2]);
                            colorbuf.put(((c >> 16) & 0xff) / 255.0);
                            colorbuf.put(((c >> 8) & 0xff) / 255.0);
                            colorbuf.put(((c >> 0) & 0xff) / 255.0);
                        }
                        vertbuf.rewind();
                        colorbuf.rewind();
                        gl.glVertexPointer(3, GL.GL_DOUBLE, 0, vertbuf);
                        gl.glColorPointer(3, GL.GL_DOUBLE, 0, colorbuf);
                        gl.glDrawArrays(GL.GL_POINTS, 0, ps.size());
                        // gl.glFlush();
                    }
                    gl.glDisableClientState(GL.GL_VERTEX_ARRAY);
                    gl.glDisableClientState(GL.GL_COLOR_ARRAY);
                }
                else
                {
                    gl.glColor3f(1, 1, 0);
                    gl.glPointSize(2);
                    gl.glBegin(GL.GL_POINTS);
                    for (ArrayList<double[]> ps : points)
                    {
                        for (double p[] : ps)
                        {
                            VisUtil.setColor(gl, cm.mapColor(p[2] - pt.get().pos[2]));
                            gl.glVertex3dv(p, 0);
                        }
                    }
                    gl.glEnd();
                }
            }
        }
    }
}
