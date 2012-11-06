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
        int nstrs = v.strs.size();
        int nresc = v.types.size();

        byte[][] strs = new byte[nstrs][];
        for (int i = 0; i < strs.length; i++)
            strs[i] = v.strs.get(i).getBytes();

        update_buffer(name.getBytes(),
                      ncodes,
                      v.codes.getData(),
                      nstrs,
                      strs,
                      nresc,
                      v.types.getData(), v.resArrs.toArray(new Object[0]),
                      v.lengths.getData(), v.ids.getData()
            );

    }


    public static void initialize()
    {
        System.loadLibrary("jvx");

        gl_initialize();
    }


    private static native int update_buffer(byte[] buf_name, int ncodes, int[] codes,
                                            int nstrs, byte[][] strs, int nresc, int types[], Object[] rescArrs, int lengths[], long ids[]);
    private static native int gl_initialize();
    private static native int fbo_create(int width, int height);
}
