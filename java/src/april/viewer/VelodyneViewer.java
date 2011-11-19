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
public class VelodyneViewer implements ViewObject, LCMSubscriber
{
    Config                         config;
    double                         spos[], squat[];
    PoseTracker                    pt         = PoseTracker.getSingleton();
    String                         channel;
    VisLayer                       vl;
    VisWorld                       vw;
    LCM                            lcm        = LCM.getSingleton();
    VelodyneCalibration            calib      = VelodyneCalibration.makeMITCalibration();
    int                            lastbucket = 0;
    ArrayList<ArrayList<double[]>> points = new ArrayList<ArrayList<double[]>>();
    ArrayList<VzPoints> visPoints = new ArrayList<VzPoints>();

    public VelodyneViewer(Viewer viewer, Config config, String channel)
    {
        this.channel = channel;
        this.vw = viewer.getVisWorld();
        this.vl = viewer.getVisLayer();
        // sensor position in robot frame
        this.spos = ConfigUtil.getPosition(config, channel);
        this.squat = ConfigUtil.getQuaternion(config, channel);

        for (int i = 0; i < 360; i++) {
            points.add(new ArrayList<double[]>());
            visPoints.add(null);
        }

        lcm.subscribe(channel, this);
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
            VisWorld.Buffer vb = vw.getBuffer(this.channel);
            Velodyne v = new Velodyne(calib, vdata.data);
            Velodyne.Sample vs = new Velodyne.Sample();
            // ArrayList<double[]> points = new ArrayList<double[]>();
            double B2G[][] = LinAlg.quatPosToMatrix(pose.orientation, pose.pos);
            double S2B[][] = LinAlg.quatPosToMatrix(squat, spos);
            double T[][] = LinAlg.matrixAB(B2G, S2B);

            // Step 1: Process the points
            while (v.next(vs))
            {
                // System.out.printf("%15f\n", vs.ctheta);
                int bucket = (int) (vs.ctheta * (360 / (2 * Math.PI)));
                if (bucket != lastbucket)
                {
                    points.get(bucket).clear();
                    lastbucket = bucket;
                }

                visPoints.set(bucket, null);
                points.get(bucket).add(LinAlg.transform(T, vs.xyz));

            }

            // Step 2: Make VisColorData and VisVertexData for any bucket that has changed

            ColorMapper cm = ColorMapper.makeJetWhite(-1, +3);
            for (int i = 0; i < visPoints.size(); i++) {
                if (visPoints.get(i) == null) {
                    VzPoints  vp = new VzPoints(new VisVertexData(points.get(i)),
                                                new VzPoints.Style(cm.makeColorData(points.get(i),2), 1));

                    visPoints.set(i,vp);
                }
                vb.addBack(visPoints.get(i));
            }
            vb.swap();
        }
    }
}
