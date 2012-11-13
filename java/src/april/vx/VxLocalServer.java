package april.vx;

import java.util.*;

public class VxLocalServer implements VxServer
{

    // For each buffer, keep track of which guids have been uploaded
    HashSet<VxResource> rescSet = new HashSet();

    public VxLocalServer(int width, int height)
    {
        initialize();

        int id = fbo_create(width, height);

    }

    // Should result in an atomic update to the program database
    public void update(String name, ArrayList<VxResource> resources, VxCodeOutputStream codes)
    {

        // Step 1: Determine which resources are new, and which are no longer needed.

        HashSet<VxResource> existing = bufferRescMap.get(name);
        if (existing == null) existing = new HashSet();

        HashSet<VxResource> next = new HashSet(resources);
        HashSet<VxResource> send = new HashSet(resources);
        send.removeAll(stale); // Don't resen

        stale.removeAll(next);


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
    static native int render(int width, int height);
    static native int read_pixels(int width, int height, byte[] img);
    private static native int fbo_create(int width, int height);
}
