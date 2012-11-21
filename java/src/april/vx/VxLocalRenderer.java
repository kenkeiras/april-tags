package april.vx;

import java.util.*;
import java.util.concurrent.*;

import april.jmat.*;

// Thin wrapper via JNI around the C side vx_local_renderer

// Note: all access to this class must be synchronous across all instances!
// XXX Probably makes the most sense to handle that synchronization here.
public class VxLocalRenderer extends VxRenderer
{
    // There can be many local
    private final long instanceID;

    // Will trigger as soon as this class is loaded, for any reason,
    // even if no rendering going to be performed
    static {
        System.loadLibrary("jvx");
    }

    // XXX This thread can be removed once synchronous  GL access has been
    // implemented on the C Side
    static GLThread gl_thread = new GLThread();

    public VxLocalRenderer(String url)
    {
        if (!url.startsWith("java://"))
            throw new IllegalArgumentException("VxLocalRenderer only accepts java:// urls");

        CreateTask ct = new CreateTask();
        gl_thread.add_task(ct);
        instanceID = ct.getValue(); // blocks until the task runs
    }

    @Override
    public void finalize()
    {
        gl_thread.add_task(new DestroyTask(instanceID));
    }

    //*** Methods for all VxRenderers ***//
    public void add_resources(HashSet<VxResource> resources)
    {
        gl_thread.add_task(new AddResourceTask(resources));

    }


    public void update_codes(String buffer_name, VxCodeOutputStream codes)
    {
        byte codeData[] = codes.getBuffer();
        int codeLen = codes.size();

        update_codes(instanceID, VxUtil.copyStringZ(buffer_name), codeLen, codeData);
    }


    public void remove_resources(HashSet<VxResource> resources)
    {
        long deallocate_guids[] = new long[resources.size()];

        int j = 0;
        for (VxResource vr : resources)
            deallocate_guids[j++] = vr.id;

        System.out.printf("Freeing %d resources\n", deallocate_guids.length);

        deallocate_resources(instanceID, deallocate_guids, deallocate_guids.length);
    }

    // Fast for a local implementation
    public int[] get_canvas_size()
    {
        int dim[] = {0,0};

        get_canvas_size(instanceID, dim);
        return dim;
    }


    public void read_pixels(int width, int height, byte[] img)
    {
        read_pixels( instanceID, width, height, img);
    }

    public void render(int width, int height)
    {
        render(instanceID, width, height);
    }


    // Takes as input a row-major projection-model matrix which is
    // generally prepended to a user specified model matrix before
    // rendering each program
    // public void set_system_pm_matrix(double pm[][])
    // {
    //     set_system_pm_matrix(LinAlg.copyFloats(pm));
    // }

    public void set_system_pm_matrix(float pm[][])
    {
        float pm2[] = new float[16];
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++)
                pm2[i*4 + j] = pm[i][j];
        set_system_pm_matrix(instanceID, pm2);
    }




    // GL Thread management


    // A single instance of this thread is created to
    // manage any GL calls


    public static class GLThread extends Thread
    {
        ArrayBlockingQueue<Runnable> tasks = new ArrayBlockingQueue<Runnable>(10);

        public void add_task(Runnable t)
        {
            tasks.offer(t); // XXX Not doing any duplicate checking. Why does GLManager need to do it?
        }
        public void run()
        {
            init(); // XXX This needs to be called from the GL thread
            while(true) {

                Runnable r;

                try {
                    r = tasks.take();
                } catch (InterruptedException ex) {
                    System.out.println("VxLocalRenderer interrupted "+ex);
                    break;
                }

                try {
                    r.run();
                } catch (Exception ex) {
                    System.out.println("VXLocalRenderer task "+r+" had exception "+ex);
                    ex.printStackTrace();
                    System.exit(-1);
                }


            }
        }
    }



    public static class DestroyTask implements Runnable
    {
        long id = 0;
        public DestroyTask(long id)
        {
            this.id = id;
        }
        public void run()
        {
            destroy(id);
        }
    }

    public static class CreateTask implements Runnable
    {
        long id = 0;
        public void run()
        {
            id = create();

            synchronized(CreateTask.this)
            {
                CreateTask.this.notifyAll();
            }
        }

        public long getValue()
        {
            synchronized(CreateTask.this)
            {
                try {
                    CreateTask.this.wait();
                } catch(InterruptedException e){}
            }
            return id;
        }
    }


    public class AddResourceTask implements Runnable
    {
        HashSet<VxResource> resources;
        public AddResourceTask(HashSet<VxResource> resources)
        {
            this.resources = resources;
        }

        public void run()
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

    }

    // Native methods
    private static native int init();
    private static native long create();
    private static native int destroy(long instanceID);

    private static native int add_resources(long instanceID, int nresc,
                                            int types[], Object[] rescs, int counts[], int fieldwidths[], long ids[]);
    private static native int update_codes(long instanceID, byte[] buf_name, int code_len, byte[] codes);
    private static native int deallocate_resources(long instanceID, long[] guids, int nguids);
    private static native int render(long instanceID, int width, int height);
    private static native int read_pixels(long instanceID, int width, int height, byte[] img);
    private static native int get_canvas_size(long instanceID, int dim[]);
    native static int set_system_pm_matrix(long instanceID, float pm[]);
}
