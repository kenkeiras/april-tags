package april.vis;

import java.io.*;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.lang.reflect.*;

import lcm.lcm.*;

public class VisSerialize
{
    // Eventually, it'd be nice to also save the camera information

    public static void writeToFile(VisCanvas vc, String filename)
    {
        try  {
            LCMDataOutputStream out = serialize(vc);
            FileOutputStream fout = new FileOutputStream(filename);
            fout.write(out.getBuffer(), 0, out.size());
            fout.close();
        } catch(IOException e) {
            System.out.println("WRN: Failed to write vc to"+filename+" ex: "+e); e.printStackTrace();
        }
        System.out.println("Wrote canvas to "+filename);
    }

    public static LCMDataOutputStream serialize(VisCanvas vc) throws IOException
    {

        // Step 0 : Copy the camera
        // XXX

        // Step 1: Get all the objects out
        LCMDataOutputStream out = new LCMDataOutputStream();
        HashMap<String, ArrayList<VisObject>> fronts = new HashMap<String, ArrayList<VisObject>>();
        // 1a) Copy out all the "front" parts of each Buffer (don't 'lock' vis this whole time)
        synchronized(vc.world.buffers) {
            for(String key : vc.world.bufferMap.keySet()) {
                VisWorld.Buffer buf  = vc.world.bufferMap.get(key);
                fronts.put(key, buf.getFront());
            }
        }

        // 1b) Serialize each buffer
        LCMDataOutputStream gout = new LCMDataOutputStream(); //global out
        for (String key : fronts.keySet()) {
            System.out.println("DBG: Processing buffer "+key);

            gout.writeChars(key);
            // Get only the objects for this buffer
            LCMDataOutputStream bout = new LCMDataOutputStream(); //buffer out
            for (VisObject obj : fronts.get(key)) {
                if (!(obj instanceof VisSerializable)) {
                    System.out.println("DBG: Skipping "+obj.getClass().getName());
                    continue;
                }
                serialize((VisSerializable)obj, bout);
            }
            gout.writeInt(bout.size());
            gout.write(bout.getBuffer(),0,bout.size());
        }

        return gout;
    }

    public static void serialize(VisSerializable o, DataOutput out) throws IOException
    {
        System.out.println("DBG:  "+o.getClass().getName());

        // Write the name of the class e.g. april.vis.VisBox
        out.writeChars(o.getClass().getName());

        // Encode this object in it's own data stream
        LCMDataOutputStream  dout = new LCMDataOutputStream();
        o.serialize(dout);

        // Write the number of bytes for this object, and then the data
        out.writeInt(dout.size());
        out.write(dout.getBuffer(),0,dout.size());
    }

    public static VisWorld readSnapshot(String input_file)
    {
        return null;
    }

    public static void main(String args[])
    {
        VisWorld vw = readSnapshot(args[0]);
        VisCanvas vc = new VisCanvas(vw);

        JFrame jf = new JFrame("VisSnapshot: "+args[0]);
        jf.add(vc);
        jf.setSize(640,480);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);
    }
}