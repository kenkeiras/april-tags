package april.vx;

import java.util.*;

public class VxLocalServer implements VxServer
{

    public VxLocalServer(int width, int height)
    {
        initialize();

        int id = fbo_create(width, height);

    }

    // Should result in an atomic update to the program database
    public void update(String name, ArrayList<VxResource> resources, VxCodeOutputStream codes)
    {

        byte codeData[] = codes.getBuffer();
        int codeLen = codes.size();


        // Process all the resources, and compact them into primitive arrays
        // where possible, theoretically makes jni faster than doing cumbersome
        // object access
        int nresc = resources.size();
        int types[] = new int[nresc];
        Object rescs[] = new Object[nresc];
        int counts[] = new int[nresc];
        int fieldwidths[] = new int[nresc];
        long ids [] = new long[nresc];

        for (int i = 0; i < resources.size(); i++) {
            VxResource r = resources.get(i);
            types[i] = r.type;
            rescs[i] = r.res;
            counts[i] = r.count;
            fieldwidths[i] = r.fieldwidth;
            ids[i] = r.id;
        }

        update_buffer(name.getBytes(),
                      codeLen, codeData,
                      nresc, types, rescs, counts, fieldwidths, ids);
    }


    public static void initialize()
    {
        System.loadLibrary("jvx");

        gl_initialize();
    }


    private static native int update_buffer(byte[] buf_name, int code_len, byte[] codes,
                                            int nresc, int types[], Object[] rescs, int counts[], int fieldwidths[], long ids[]);
    private static native int gl_initialize();
    // XXX access control
    static native int render();
    static native int read_pixels(int width, int height, byte[] img);
    private static native int fbo_create(int width, int height);
}
