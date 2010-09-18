package april.sim;

import java.io.*;
import java.awt.*;
import java.awt.image.*;
import java.util.*;

import lcm.lcm.*;

import april.lcmtypes.*;
import april.jmat.*;
import april.util.*;
import april.vis.*;
import april.lcm.*;
import april.vis.*;

public class Sensors
{
    static Object vocObj = new Object();
    static VisOffscreenCanvas voc;

    public static BufferedImage camera(VisWorld vw,
                                       double eye[], double lookAt[], double up[],
                                       double fovy_degrees,
                                       int width, int height)
    {
        synchronized(vocObj) {

            if (voc == null) {
                voc = new VisOffscreenCanvas(width, height, vw);
                voc.getViewManager().interfaceMode = 3.0; // critical! allow any viewpoint (don't move camera!)
            } else {
                voc.setSize(width, height);
            }

            VisView view = voc.getViewManager().viewGoal;
            view.zclip_near = 0.1;
            view.perspective_fovy_degrees = fovy_degrees;
            view.eye = eye;
            view.lookAt = lookAt;
            view.up = up;

            VisOffscreenCanvas.RenderData rd = voc.getImageData(false);
            return rd.im;
        }
    }

    public static double[] laser(SimWorld w, HashSet<SimObject> ignore,
                                 double T[][],
                                 int nranges, double rad0, double radstep)
    {
        double ranges[] = new double[nranges];

        double eye[] = new double[] { T[0][2], T[1][2], T[2][2] };

        for (int i = 0; i < ranges.length; i++) {
            double dir[] = LinAlg.transform(T, new double[] { Math.cos(rad0 + i*radstep),
                                                              Math.sin(rad0 + i*radstep),
                                                              0 });
            ranges[i] = w.collisionRay(eye, dir, ignore);
        }

        return ranges;
    }
}
