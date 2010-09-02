package april.vis;

import java.io.*;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.lang.reflect.*;

import lcm.lcm.*;

import april.util.*;

public class VisSerialize
{
    // Eventually, it'd be nice to also save the camera information

    public static void writeVCToFile(VisCanvas vc, String filename)
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

            gout.writeStringZ(key);
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

    public static void serialize(VisSerializable o, LCMDataOutputStream out) throws IOException
    {
        System.out.println("DBG:  "+o.getClass().getName());

        // Write the name of the class e.g. april.vis.VisBox
        out.writeStringZ(o.getClass().getName());

        // Encode this object in it's own data stream
        LCMDataOutputStream  dout = new LCMDataOutputStream();
        o.serialize(dout);

        // Write the number of bytes for this object, and then the data
        out.writeInt(dout.size());
        out.write(dout.getBuffer(),0,dout.size());
    }

    public static VisSerializable unserialize(LCMDataInputStream in) throws IOException
    {
        // Grab class name
        String obj_name = in.readStringZ();
        System.out.println("DBG: Loading "+obj_name);

        // Grab obj. data
        int olen = in.readInt();
        byte obj_buf[] = new byte[olen];
        in.readFully(obj_buf);
        LCMDataInputStream obj_in = new LCMDataInputStream(obj_buf);

        // Instantiate
        VisSerializable obj = (VisSerializable)ReflectUtil.createObject(obj_name);
        if (obj == null) {
            System.out.println("WRN Failed to read class "+obj_name+"!");
            return null;
        }

        // Unserialize
        obj.unserialize(obj_in);
        assert(obj_in.available() == 0);

        return obj;
    }

    public static VisCanvas readVCFromFile(String filename)
    {
        try  {
            File f = new File(filename);

            byte buffer[] = new byte[(int)f.length()];
            FileInputStream fin = new FileInputStream(filename);
            int len = fin.read(buffer);
            if (len != buffer.length) {
                System.out.println("WRN: Failed to read file fully "+filename);
                return null;
            }
            fin.close();

            LCMDataInputStream in = new LCMDataInputStream(buffer);

            return unserializeVC(in);
        } catch(IOException e) {
            System.out.println("WRN: Failed to read vc from"+filename+" ex: "+e); e.printStackTrace();
        }
        return null;
    }

    public static VisCanvas unserializeVC(LCMDataInputStream global_in) throws IOException
    {
        VisWorld vw = new VisWorld();
        // Read each buffer individually
        while(global_in.available() > 0) {
            String buf_name = global_in.readStringZ();
            VisWorld.Buffer vb = vw.getBuffer(buf_name);
            int len = global_in.readInt();
            System.out.println("DBG: Reading buffer "+buf_name + " len ="+len);
            byte buf[] = new byte[len];
            global_in.readFully(buf);
            LCMDataInputStream buffer_in = new LCMDataInputStream(buf);
            while (buffer_in.available() > 0) {
                VisSerializable obj = unserialize(buffer_in);
                if (obj == null) {
                    System.out.println("Can't continue reading buffer "+buf_name+"!");
                    break;
                }
                // Add to world
                vb.addBuffered((VisObject)obj);
            }
            vb.switchBuffer();
        }

        return new VisCanvas(vw);
    }

    public static void main(String args[])
    {
        VisCanvas vc = readVCFromFile(args[0]);

        JFrame jf = new JFrame("VisSnapshot: "+args[0]);
        jf.add(vc);
        jf.setSize(640,480);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);
    }
}