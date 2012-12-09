package april.vx;

import april.jmat.geom.*;

public interface VxManipulationManager
{
    public double[] pickManipulationPoint(VxCanvas vc, VxLayer vl, VxCanvas.RenderInfo rinfo, GRay3D ray);
}
