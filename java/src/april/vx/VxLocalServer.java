package april.vx;


public class VxLocalServer implements VxServer
{

    public VxLocalServer(int width, int height)
    {
        initialize();

        int id = fbo_create(width, height);

    }

    // Should result in an atomic update to the program database
    public void update(String name, VxObjOpcodes v)
    {

        int ncodes = v.codes.size();
        int nresc = v.resources.size();


        int codes[] = v.codes.getData();

        // Process all the resources, and compact them into primitive arrays
        // where possible, theoretically makes jni faster than doing cumbersome
        // object access

        byte names[][] = new byte[nresc][];
        int types[] = new int[nresc];
        Object rescs[] = new Object[nresc];
        int counts[] = new int[nresc];
        int fieldwidths[] = new int[nresc];
        long ids [] = new long[nresc];

        for (int i = 0; i < v.resources.size(); i++) {
            VxObjOpcodes.Resource r = v.resources.get(i);
            if (r.name == null)
                names[i] = new byte[]{'\0'};
            else
                names[i] = r.name;
            types[i] = r.type;
            rescs[i] = r.res;
            counts[i] = r.count;
            fieldwidths[i] = r.fieldwidth;
            ids[i] = r.id;
        }

        update_buffer(name.getBytes(),
                      ncodes,
                      codes,
                      nresc,
                      names,
                      types, rescs, counts, fieldwidths, ids);

                      // v.types.getData(), v.resArrs.toArray(new Object[0]),
                      // v.lengths.getData(), v.ids.getData()
            // );

    }


    public static void initialize()
    {
        System.loadLibrary("jvx");

        gl_initialize();
    }


    private static native int update_buffer(byte[] buf_name, int ncodes, int[] codes,
                                            int nresc, byte[][] strs, int types[], Object[] rescs, int counts[], int fieldwidths[], long ids[]);
    private static native int gl_initialize();
    private static native int fbo_create(int width, int height);
}
