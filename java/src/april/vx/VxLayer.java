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

    // Each layer gets a unique ID to allow server to manage multiple layersx
    final int layerID = layerNextID.getAndIncrement();


    VxLayer(VxRenderer _rend, VxWorld _vw)
    {
        vw = _vw;
        rend = _rend;


        update();
    }


    private void update()
    {
        // by default we use the entire screen;
        float viewport_rel[] = {0.0f,0.0f,1.0f,1.0f};

        // Don't use code stream, since it's relatively short, and since we need the meta data about viewport size
        // to know about event handling on server side
        rend.update_layer(layerID, vw.worldID, viewport_rel);
    }

}