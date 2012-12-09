package april.vx;


import java.util.concurrent.atomic.*;

// The layer encapsulates drawing a World in a specific viewport, as
// well as any event handling and camera management for that viewport
// The layer is split between state which needs to be pushed when it
// changes (such as the viewport size), and state which is pulled
// pulled when a rendering operation occurs. In practice, the later only occurs if
// this layer is attached to a local rendering context, though this
// layer is oblivious to that fact

public class VxLayer
{
    // These don't need to be long, but we've already got the c data structures for longs.
    static AtomicInteger layerNextID = new AtomicInteger(1);

    VxWorld vw;
    VxRenderer rend;

    // Managers. Note that they only work when connected to a local rendering context. (VxCanvas will poll)
    VxCameraManager cameraManager = new DefaultCameraManager();

    // Each layer gets a unique ID to allow server to manage multiple layersx
    final int layerID = layerNextID.getAndIncrement();
    int drawOrder = 0;

    // by default we use the entire screen;
    private float viewport_rel[] = {0.0f,0.0f,1.0f,1.0f};

    VxLayer(VxRenderer _rend, VxWorld _vw)
    {
        vw = _vw;
        rend = _rend;


        update();
    }

    public void set_viewport(float _viewport_rel[])
    {
        viewport_rel = _viewport_rel;
        update();
    }


    public int[] getAbsoluteViewport(int width, int height)
    {
        int layerViewport[] = {(int)(width  * viewport_rel[0]),
                               (int)(height * viewport_rel[1]),
                               (int)(width  * viewport_rel[2]),
                               (int)(height * viewport_rel[3])};
        return layerViewport;
    }
    private void update()
    {

        // Don't use code stream, since it's relatively short, and since we need the meta data about viewport size
        // to know about event handling on server side
        rend.update_layer(layerID, vw.worldID, drawOrder, viewport_rel);
    }

}