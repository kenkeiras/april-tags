package april.vx;

import java.util.*;


// Class which maintains a VxLocalRenderer instance (preferably through some synchronous wrapper)
// Also can be painted as a component
public class VxCanvas
{

    VxLocalRenderer rend;

    public VxCanvas(VxLocalRenderer rend)
    {
        this.rend = rend;
    }


    // implement draw methods, frame rate, camera controls, etc


}