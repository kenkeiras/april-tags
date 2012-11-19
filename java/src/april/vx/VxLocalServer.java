package april.vx;

import java.util.*;

public class VxLocalServer implements VxServer
{

    // For each buffer, keep track of which guids have been uploaded
    HashMap<String, HashSet<VxResource>> liveSet = new HashMap();

    HashSet<VxResource> remoteResources = new HashSet();

    public VxLocalServer(int width, int height)
    {
        initialize();

        int id = fbo_create(width, height);

    }

    // Should result in an atomic update to the program database
    public synchronized void update(String name, HashSet<VxResource> resources, VxCodeOutputStream codes)
    {

        // Step 1: Determine which resources are new, and need to be sent:

        liveSet.put(name, resources);

        HashSet<VxResource> send = new HashSet(resources);
        send.removeAll(remoteResources);
        remoteResources.addAll(send);

        byte codeData[] = codes.getBuffer();
        int codeLen = codes.size();


        // Process all the resources, and compact them into primitive arrays
        // where possible, theoretically makes jni faster than doing cumbersome
        // object access
        int nresc = send.size();
        int types[] = new int[nresc];
        Object rescs[] = new Object[nresc];
        int counts[] = new int[nresc];
        int fieldwidths[] = new int[nresc];
        long ids [] = new long[nresc];

        int i = 0;
        for (VxResource r : send) {
            types[i] = r.type;
            rescs[i] = r.res;
            counts[i] = r.count;
            fieldwidths[i] = r.fieldwidth;
            ids[i] = r.id;

            i++;
        }

        update_buffer(name.getBytes(),
                      codeLen, codeData,
                      nresc, types, rescs, counts, fieldwidths, ids);

        // Now remove any stale resources:
        ArrayList<VxResource> deallocate = new ArrayList();
      outer:
        for (VxResource vresc : remoteResources) {
            for (HashSet<VxResource> live : liveSet.values())
                if (live.contains(vresc))
                    continue outer;

            // if we make it here, we need to release vresc
            deallocate.add(vresc);
        }
        remoteResources.removeAll(deallocate);


        long deallocate_guids[] = new long[deallocate.size()];
        for (int j = 0; j < deallocate_guids.length; j++)
            deallocate_guids[j] = deallocate.get(j).id;

        System.out.printf("Freeing %d resources\n", deallocate_guids.length);
        // XXX deallocate_resources(deallocate_guids, deallocate_guids.length);
        deallocate_resources(deallocate_guids, deallocate_guids.length);
    }

    public static void initialize()
    {
        System.loadLibrary("jvx");

        gl_initialize();
    }


    private static native int update_buffer(byte[] buf_name, int code_len, byte[] codes,
                                            int nresc, int types[], Object[] rescs, int counts[], int fieldwidths[], long ids[]);

    private static native int deallocate_resources(long[] guids, int nguids);

    private static native int gl_initialize();
    // XXX access control
    static native int render(int width, int height);
    static native int read_pixels(int width, int height, byte[] img);
    private static native int fbo_create(int width, int height);
    native static int set_system_pm_matrix(float pm[]);

    // Flattens the matrix to send via jni
    public static int set_system_pm_matrix(float pm [][])
    {
        float pm2[] = new float[16];
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++)
                pm2[i*4 + j] = pm[i][j];

        return set_system_pm_matrix(pm2);
    }
}
