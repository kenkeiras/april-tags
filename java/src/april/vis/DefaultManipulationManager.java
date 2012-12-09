package april.vx;

import java.io.*;

import april.jmat.geom.*;

/** Picks a manipulation point based on the intersection of the ray
 * with the XY plane (by default at z=0) **/
public class DefaultManipulationManager implements VxManipulationManager
{
    public double z = 0;

    public DefaultManipulationManager()
    {
    }

    public double[] pickManipulationPoint(VxCanvas vc, VxLayer vl, VxCanvas.RenderInfo rinfo, GRay3D ray)
    {
        return ray.intersectPlaneXY(z);
    }
}
