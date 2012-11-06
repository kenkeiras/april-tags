package april.vx;


public class VxLocalServer implements VxServer
{

    // Should result in an atomic update to the program database
    public void update(String name, VxObjOpcodes voo)
    {

        update_buffer(name);

    }


    static{ //XXX
        initialize();
    }

    public static void initialize()
    {
        System.loadLibrary("jvx");

        // XXX gl_initialize();
    }


    private static native int update_buffer(String name);
}