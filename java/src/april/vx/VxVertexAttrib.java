package april.vx;

import java.util.*;

public class VxVertexAttrib
{
    // final long id = VxUtil.allocateID();

    // final float fdata[];
    final VxResource vr;
    final int dim;

    // XXX Also add integers, bytes

    public VxVertexAttrib(VxResource vr, int dim)
    {
        this.vr = vr;
        this.dim  = dim;
    }

    // Only accepts ArrayList<double[]> or ArrayList<float[]>
    public VxVertexAttrib(List points)
    {
        this.dim  = java.lang.reflect.Array.getLength(points.get(0));

        float fdata[] = new float[points.size()*dim];

        if(double[].class == points.get(0).getClass()) {
            for (int i = 0; i < points.size(); i++) {
                double pt[] = (double[]) points.get(i);
                for (int j = 0; j < dim; j++)
                    fdata[i*dim + j] = (float)pt[j];
            }
        } else if (float[].class == points.get(0).getClass()) {
            for (int i = 0; i < points.size(); i++) {
                float pt[] = (float[]) points.get(i);
                for (int j = 0; j < dim; j++)
                    fdata[i*dim + j] = pt[j];
            }
        } else {
            // In theory we could also handle automatically casting ints to floats this way
            throw new IllegalArgumentException("VxVertexAttrib() only accepts arraylists of double[] or float[]");
        }

        vr = new VxResource(Vx.GL_FLOAT, fdata, fdata.length, 4, VxUtil.allocateID());
    }

    public int size()
    {
        return vr.count / dim;
    }

}