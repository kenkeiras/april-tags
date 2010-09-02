package april.vis;

import java.io.*;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.lang.reflect.*;

import lcm.lcm.*;

import april.util.*;

/**
 * How-to make your VisObject also be VisSerializable.
 *  1. Implement the VisSerializable Interface
 *  2. Implement an public, no-args constructor
 *  3. Fill in the serialize, and unserialize methods in aforementioned interface
 *    a) If you have children that are VisObjects and VisSerializable,
 *       you can use the methods in this class:
 *             void serialize(VisSerializable, LCMDataOutputStream)
 *                             -- and --
 *             VisSerializable unserialize(LCMDataInputStream)
 *        to make your life easier.
 *     b) Refer to LCMDataInput/OutputStream for how to serialize primitive types
 *     c) If you have children that are VisObjets, but not serializable, you should
 *        not serialize them! (or modify those classes..)
 *  4. For reference, consult a simple example (e.g VisBox), a medium example
 *    (VisChain, VisData), or a 'hard' example (VisTexture)
 *
 *  NOTE: please be aware of the distinction between VisSerializable and
 *        VisSerialize if you get compilation errors
 */

/**
 * How-to load your .vis snapshot:  java april.vis.VisSerialize <path-to-snapshot>
 * How-to save your .vis snapshot:  call VisSerialzie.serialzie(VC) (API) or use VisCanvasPopopMenu (GUI)
 */
public class VisSerialize
{
    static boolean debug = false;

    public static void serialize(VisSerializable o, LCMDataOutputStream out) throws IOException
    {
        if (debug) System.out.println("DBG:  "+o.getClass().getName());

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
        if (debug) System.out.println("DBG: Loading "+obj_name);

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

    // The rest of this file pertains to writing the VisCanvas to a file
    public static void writeVCToFile(VisCanvas vc, String filename)
    {
        try  {
            LCMDataOutputStream out = serializeVC(vc);
            FileOutputStream fout = new FileOutputStream(filename);
            fout.write(out.getBuffer(), 0, out.size());
            fout.close();
        } catch(IOException e) {
            System.out.println("WRN: Failed to write vc to"+filename+" ex: "+e); e.printStackTrace();
        }
        System.out.println("Wrote canvas to "+filename);
    }

    public static LCMDataOutputStream serializeVC(VisCanvas vc) throws IOException
    {

        // Step 1: Get all the objects out
        HashMap<String, ArrayList<VisObject>> fronts = new HashMap<String, ArrayList<VisObject>>();
        // 1a) Copy out all the "front" parts of each Buffer (don't 'lock' vis this whole time)
        synchronized(vc.world.buffers) {
            for(String key : vc.world.bufferMap.keySet()) {
                VisWorld.Buffer buf  = vc.world.bufferMap.get(key);
                fronts.put(key, buf.getFront());
            }
        }

        LCMDataOutputStream gout = new LCMDataOutputStream(); //global out

        // Step 0 : Copy the camera
        serialize(vc.viewManager.viewGoal,gout);

        // 1b) Serialize each buffer
        for (String key : fronts.keySet()) {
            if (debug) System.out.println("DBG: Processing buffer "+key);

            gout.writeStringZ(key);
            Boolean enabled = vc.viewManager.enabledBuffers.get(key);
            gout.writeBoolean(enabled == null || enabled);
            gout.writeInt(vc.world.bufferMap.get(key).drawOrder);

            // Get only the objects for this buffer
            LCMDataOutputStream bout = new LCMDataOutputStream(); //buffer out
            for (VisObject obj : fronts.get(key)) {
                if (!(obj instanceof VisSerializable)) {
                    if (debug) System.out.println("DBG: Skipping "+obj.getClass().getName());
                    continue;
                }
                serialize((VisSerializable)obj, bout);
            }
            gout.writeInt(bout.size());
            gout.write(bout.getBuffer(),0,bout.size());
        }

        return gout;
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
        VisCanvas vc = new VisCanvas(vw);
        vc.viewManager.viewGoal = (VisView)unserialize(global_in);

        // Read each buffer individually
        while(global_in.available() > 0) {
            String buf_name = global_in.readStringZ();
            boolean enabled = global_in.readBoolean();
            VisWorld.Buffer vb = vw.getBuffer(buf_name);
            vc.viewManager.enabledBuffers.put(buf_name, enabled);
            vb.setDrawOrder(global_in.readInt());

            int len = global_in.readInt();
            if (debug) System.out.println("DBG: Reading buffer "+buf_name + " len ="+len);
            byte buf[] = new byte[len];
            global_in.readFully(buf);
            LCMDataInputStream buffer_in = new LCMDataInputStream(buf);
            while (buffer_in.available() > 0) {
                VisSerializable obj = unserialize(buffer_in);
                if (obj == null) {
                    System.out.println("WRN: Can't continue reading buffer "+buf_name+"!");
                    break;
                }
                // Add to world
                vb.addBuffered((VisObject)obj);
            }
            vb.switchBuffer();
        }
        return vc;
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