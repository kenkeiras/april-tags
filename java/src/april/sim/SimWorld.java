package april.sim;

import java.awt.*;
import java.io.*;
import java.util.*;

import april.vis.*;
import april.jmat.*;
import april.util.*;

public class SimWorld
{
    public ArrayList<SimObject> objects = new ArrayList<SimObject>();

    public SimWorld()
    {
    }

    public SimWorld(String path) throws IOException
    {
        StructureReader ins = new TextStructureReader(new BufferedReader(new FileReader(path)));

        while (true) {
            String cls = ins.readString();

            if (cls == null) // EOF?
                break;

            Object obj = ReflectUtil.createObject(cls);
            assert (obj instanceof SimObject);
            SimObject so = (SimObject) obj;

            ins.blockBegin();
            so.read(ins);
            objects.add(so);
            ins.blockEnd();
        }

        ins.close();
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

        for (SimObject so : objects) {
            outs.writeString(so.getClass().getName());
            outs.blockBegin();
            so.write(outs);
            outs.blockEnd();
        }

        outs.close();
    }
}
