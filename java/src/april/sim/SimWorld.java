package april.sim;

import java.awt.*;
import java.io.*;
import java.util.*;

import april.vis.*;
import april.jmat.*;
import april.util.*;

public class SimWorld
{
    public GPSLinearize gpslin = new GPSLinearize(new double[] { 44.22, 83.75 });

    public ArrayList<SimObject> objects = new ArrayList<SimObject>();

    public SimWorld()
    {
    }

    public SimWorld(String path) throws IOException
    {
        StructureReader ins = new TextStructureReader(new BufferedReader(new FileReader(path)));

        ins.blockBegin();
        gpslin.read(ins);
        ins.blockEnd();

        while (true) {
            String cls = ins.readString();

            if (cls == null) // EOF?
                break;

            try {
                SimObject so = createObject(this, cls);
                if (so != null) {
                    ins.blockBegin();
                    so.read(ins);
                    objects.add(so);
                    ins.blockEnd();
                }
            } catch (Exception ex) {
                System.out.println("ex: "+ex);
            }
        }

        ins.close();
    }

    public static SimObject createObject(SimWorld sw, String cls)
    {
        try {
            Object obj = Class.forName(cls).getConstructor(SimWorld.class).newInstance(sw);
            assert (obj instanceof SimObject);
            SimObject so = (SimObject) obj;
            return so;

        } catch (Exception ex) {
            System.out.println("ex: "+ex);
        }

        return null;
    }

    public void write(String path) throws IOException
    {
        FileWriter outs = new FileWriter(path);
        write(new BufferedWriter(outs));
        outs.close();
    }

    public void write(BufferedWriter _outs) throws IOException
    {
        StructureWriter outs = new TextStructureWriter(_outs);

        outs.writeComment("GPSLinearize");
        outs.blockBegin();
        gpslin.write(outs);
        outs.blockEnd();

        for (SimObject so : objects) {
            outs.writeString(so.getClass().getName());
            outs.blockBegin();
            so.write(outs);
            outs.blockEnd();
        }

        outs.close();
    }
}
