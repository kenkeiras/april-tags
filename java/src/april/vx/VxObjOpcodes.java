package april.vx;

import java.util.*;

public class VxObjOpcodes
{

    // Add methods for adding opcodes and object pointers
    IntArray codes = new IntArray();
    ArrayList<String> strs = new ArrayList();

    // Tracking resources:
    IntArray types = new IntArray();
    ArrayList<Object> resArrs = new ArrayList();
    IntArray lengths = new IntArray();
    LongArray ids = new LongArray();

    public void addCode(int c)
    {
        codes.add(c);
    }

    public void addString(String str)
    {
        strs.add(str);
    }

    public void addObject(int type, Object arr, int len,  long id)
    {
        types.add(type);
        resArrs.add(arr);
        lengths.add(len);
        ids.add(id);

    }

}