package april.vx;

public class VxVertexAttrib
{
    final long id = VxUtil.allocateID();

    final float fdata[];
    final int dim;

    // XXX Also add integers, bytes

    public VxVertexAttrib(float fdata[], int dim)
    {
        this.fdata = fdata;
        this.dim  = dim;
    }



}