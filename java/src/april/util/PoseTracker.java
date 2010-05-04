package april.util;

import april.lcmtypes.*;

import lcm.lcm.*;

import java.io.*;

/** Subscribes to pose_t and find the pose_t whose timestamp is
 * closest to the requested value. 
 **/
public class PoseTracker implements LCMSubscriber
{
    String channel;
    LCM lcm = LCM.getSingleton();

    pose_t queue[] = new pose_t[50];
    int queue_inpos = 0;

    static PoseTracker pt;
    static PoseTracker ptTruth;

    public static PoseTracker getSingleton()
    {
        if (pt == null) {
            pt = new PoseTracker("POSE");
        }
        return pt;
    }

    public static PoseTracker getTruthSingleton()
    {
        if (ptTruth == null) {
            ptTruth = new PoseTracker("POSE_TRUTH");
        }
        return ptTruth;
    }

    public PoseTracker(String channel)
    {
        this.channel = channel;
        lcm.subscribe(channel, this);
    }

    public void messageReceived(LCM lcm, String channel, LCMDataInputStream ins)
    {
        try {
            messageReceivedEx(channel, ins);
        } catch (IOException ex) {
            System.out.println("Exception: "+ex);
        }
    }

    public synchronized pose_t get()
    {
        return queue[(queue_inpos - 1 + queue.length)%queue.length];
    }

    public synchronized pose_t get(long utime)
    {
        return get(utime, false);
    }

    public synchronized pose_t get(long utime, boolean interpolate)
    {
        pose_t bestpose = null;
        long besterr = Long.MAX_VALUE;

        for (int age = 0; age < queue.length; age++) {
            int i = (queue_inpos - 1 - age + queue.length)%queue.length;

            //	for (int i = (queue_inpos+queue.length-1)%queue.length; i!=queue_inpos; i=(i+queue.length-1)%queue.length) {
            if (queue[i] == null)
                return bestpose;

            long err = Math.abs(utime - queue[i].utime);

            // error has gone up: we're done searching.
            if (err > besterr)
                break;

            bestpose = queue[i];
            besterr = err;
        }

        if (!interpolate)
            return bestpose;

        pose_t intpose = bestpose.copy();
        double dt = (utime - bestpose.utime)/1000000.0;
        for (int i = 0; i < 3; i++)
            intpose.pos[i] += intpose.vel[i]*dt;

        return intpose;
    }

    void messageReceivedEx(String channel, LCMDataInputStream ins) throws IOException
    {
        if (channel.equals(this.channel)) {
            pose_t p = new pose_t(ins);
            synchronized(this) {
                queue[queue_inpos] = p;
                queue_inpos = (queue_inpos+1)%queue.length;
            }
        }
    }
}
