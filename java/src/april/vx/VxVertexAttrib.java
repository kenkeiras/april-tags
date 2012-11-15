package april.vx;

import java.util.*;

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


    public VxVertexAttrib(ArrayList<double[]> points)
    {
        this.dim  = points.get(0).length;


        this.fdata = new float[points.size()*dim];

        for (int i = 0; i < points.size(); i++) {
            double pt[] = points.get(i);
            for (int j = 0; j < dim; j++)
                fdata[i*dim + j] = (float)pt[j];
        }
    }

    public int size()
    {
        return fdata.length;
    }



}