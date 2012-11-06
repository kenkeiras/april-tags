package april.vx;


public class VxLocalServer implements VxServer
{

    public VxLocalServer(int width, int height)
    {
        initialize();

        int id = fbo_create(width, height);

    }

    // Should result in an atomic update to the program database
    public void update(String name, VxObjOpcodes voo)
    {

        update_buffer(name);

    }


    public static void initialize()
    {
        System.loadLibrary("jvx");

        gl_initialize();
    }


    private static native int gl_initialize();
    private static native int fbo_create(int width, int height);
    private static native int update_buffer(String name);
}