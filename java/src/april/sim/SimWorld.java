package april.sim;

import java.util.*;
import java.io.*;

import april.util.*;
import april.vis.*;

/** File format: Each object is written as a group of lines. The first
    line is always the class name, and the last line is always an
    asterisk by itself. The lines between are the arguments of the
    object. **/
public class SimWorld
{
    public ArrayList<SimObject> objects = new ArrayList<SimObject>();

    public SimWorld()
    {
    }

    public SimWorld(String path) throws IOException
    {
        BufferedReader ins = new BufferedReader(new FileReader(path));

        ArrayList<String> lines = new ArrayList<String>();
        String line;
        while ((line = ins.readLine()) != null) {
            // skip empty lines
            String tline = line.trim();
            if (tline.startsWith("#") || tline.length()==0)
                continue;

            if (!line.equals("*")) {
                lines.add(line);
                continue;
            }

            if (lines.size() > 0) {
                String cls = lines.get(0);
                ByteArrayOutputStream bouts = new ByteArrayOutputStream();
                BufferedWriter outs = new BufferedWriter(new OutputStreamWriter(bouts));

                for (int i = 1; i < lines.size(); i++)
                    outs.write(lines.get(i)+"\n");

                outs.flush();

                ByteArrayInputStream bins = new ByteArrayInputStream(bouts.toByteArray());
                SimObject obj = (SimObject) ReflectUtil.createObject(cls);
                if (obj != null) {
                    obj.read(new BufferedReader(new InputStreamReader(bins)));
                    objects.add(obj);
                }

                lines.clear();
            }
        }
    }

    public double collisionSphere(double p[], HashSet<SimObject> ignore)
    {
        double r = Double.MAX_VALUE;

        for (int i = 0; i < objects.size(); i++) {
            SimObject obj = objects.get(i);
            if (ignore != null && ignore.contains(obj))
                continue;

            r = Math.min(r, obj.collisionSphere(p));
        }

        return r;
    }

    public double collisionRay(double p[], double dir[], HashSet<SimObject> ignore)
    {
        double r = Double.MAX_VALUE;

        for (int i = 0; i < objects.size(); i++) {
            SimObject obj = objects.get(i);
            if (ignore != null && ignore.contains(obj))
                continue;

            r = Math.min(r, obj.collisionRay(p, dir));
        }

        return r;
    }

    public void save(String path) throws IOException
    {
        BufferedWriter outs = new BufferedWriter(new FileWriter(path));

        for (SimObject obj : objects) {
            outs.write(obj.getClass().getName()+"\n");
            obj.write(outs);
            outs.write("*\n");
        }

        outs.flush();
        outs.close();
    }
}
