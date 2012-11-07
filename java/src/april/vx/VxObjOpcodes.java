package april.vx;

import java.util.*;

public class VxObjOpcodes
{

    // managed resource
    static class Resource
    {
        long id;

        int type;
        Object res;
        int count;
        int fieldwidth;

        byte name[]; // Null terminated string
    }

    // Add methods for adding opcodes and object pointers
    IntArray codes = new IntArray();

    // Tracking resources:
    ArrayList<Resource> resources = new ArrayList();

    public void addCode(int c)
    {
        codes.add(c);
    }

    // Name can be null (e.g. for element data
    public void addResource(String name, int type, Object arr, int count, int fieldwidth,  long id)
    {
        Resource r = new Resource();
        r.type = type;
        r.res = arr;
        r.count = count;
        r.fieldwidth = fieldwidth;
        r.id = id;

        r.name = name.getBytes();

        resources.add(r);
    }

}