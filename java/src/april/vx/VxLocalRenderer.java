package april.vx;

import java.util.*;
import java.util.concurrent.*;

import april.jmat.*;

// Thin wrapper via JNI around the C side vx_local_renderer

// Note: all access to this class must be synchronous across all instances!
// XXX Probably makes the most sense to handle that synchronization here.
public class VxLocalRenderer extends VxRenderer
{
    // There can be many local instances, (only up to 4 billion)
    private final int instanceID;

    // Will trigger as soon as this class is loaded, for any reason,
    // even if no rendering going to be performed
    static {
        System.loadLibrary("jvx");
        vx_local_initialize();
    }

    int width, height;

    VxResourceManager manager;

    public VxLocalRenderer(String url)
    {
        if (!url.startsWith("java://"))
            throw new IllegalArgumentException("VxLocalRenderer only accepts java:// urls");

        int argidx = url.indexOf("?");
        if (argidx >= 0) {
            String arg = url.substring(argidx+1);
            url = url.substring(0, argidx);

            String params[] = arg.split("&");
            for (String param : params) {
                String keyval[] = param.split("=");
                if (keyval[0].equals("width"))
                    width = Integer.parseInt(keyval[1]);
                else if (keyval[0].equals("height"))
                    height = Integer.parseInt(keyval[1]);
            }
        }

        instanceID = vx_create_local_renderer(width, height);

        manager = new VxResourceManager(this);
    }

    @Override
    public void finalize()
    {
        destroy(instanceID);
    }

    public void update_resources_managed(int worldID, String name, HashSet<VxResource> resources)
    {
        manager.update_resources_managed(worldID, name, resources);
    }


    //*** Methods for all VxRenderers ***//
    protected void add_resources_direct(HashSet<VxResource> resources)
    {
        // Process all the resources, and compact them into primitive arrays
        // where possible, theoretically makes jni faster than doing cumbersome
        // object access
        int nresc = resources.size();
        int types[] = new int[nresc];
        Object rescs[] = new Object[nresc];
        int counts[] = new int[nresc];
        int fieldwidths[] = new int[nresc];
        long ids [] = new long[nresc];

        int i = 0;
        for (VxResource r : resources) {
            types[i] = r.type;
            rescs[i] = r.res;
            counts[i] = r.count;
            fieldwidths[i] = r.fieldwidth;
            ids[i] = r.id;

            i++;
        }

        add_resources(instanceID, nresc, types, rescs, counts, fieldwidths, ids);
    }

    // Set the viewport and worldID for a specific layer
    public void update_layer(int layerID, int worldID, int drawOrder, float viewport_rel[])
    {
        update_layer(instanceID, layerID, worldID, drawOrder, viewport_rel);
    }


    public void update_buffer(int worldID, String buffer_name, int drawOrder, VxCodeOutputStream codes)
    {
        byte codeData[] = codes.getBuffer();
        int codeLen = codes.size();

        update_buffer(instanceID, worldID, VxUtil.copyStringZ(buffer_name), drawOrder, codeLen, codeData);
    }


    public void remove_resources_direct(HashSet<VxResource> resources)
    {
        long deallocate_guids[] = new long[resources.size()];

        int j = 0;
        for (VxResource vr : resources)
            deallocate_guids[j++] = vr.id;


        deallocate_resources(instanceID, deallocate_guids, deallocate_guids.length);
    }

    // Fast for a local implementation
    public int[] get_canvas_size()
    {
        int dim[] = {width,height};
        // synchronized(gl_thread)
        // {
        //     get_canvas_size(instanceID, dim);
        // }
        return dim;
    }

    public void render(int width, int height, byte[] img, int format)
    {
        // Update dimensions
        this.width = width;
        this.height = height;


        render(instanceID, width, height, img, format);
    }

    // Takes as input a row-major projection-model matrix which is
    // generally prepended to a user specified model matrix before
    // rendering each program
    // public void set_system_pm_matrix(double pm[][])
    // {
    //     set_system_pm_matrix(LinAlg.copyFloats(pm));
    // }

    public void set_layer_pm_matrix(int layerID, float pm[][])
    {
        float pm2[] = new float[16];
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++)
                pm2[i*4 + j] = pm[i][j];
        set_layer_pm_matrix(instanceID, layerID, pm2);
    }

    // Native methods
    private static native int vx_local_initialize();
    private static native int vx_create_local_renderer(int width, int height);
    private static native int destroy(int instanceID);

    private static native int add_resources(int instanceID, int nresc,
                                            int types[], Object[] rescs, int counts[], int fieldwidths[], long ids[]);
    private static native int update_buffer(int instanceID, int worldID, byte[] buf_name, int draw_order, int code_len, byte[] codes);
    private static native void update_layer(int instanceID, int layerID, int worldID, int draw_order, float viewport_rel[]);
    private static native void deallocate_resources(int instanceID, long[] guids, int nguids);
    private static native int render(int instanceID, int width, int height, byte[] img, int format);
    private static native int get_canvas_size(int instanceID, int dim[]);
    native static void set_layer_pm_matrix(int instanceID, int layerID, float pm[]);
}
