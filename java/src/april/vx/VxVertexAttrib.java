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

    // Only accepts ArrayList<double[]> or ArrayList<float[]>
    // public VxVertexAttrib(ArrayList points)
    // {
    //     this.dim  = java.lang.reflect.Array.getLength(points.get(0));

    //     this.fdata = new float[points.size()*dim];

    //     if(double[].class == points.get(0).getClass()) {
    //         for (int i = 0; i < points.size(); i++) {
    //             double pt[] = (double[]) points.get(0);
    //             for (int j = 0; j < dim; j++)
    //                 fdata[i*dim + j] = (float)pt[j];
    //         }
    //     } else if (float[].class == points.get(0).getClass()) {
    //         for (int i = 0; i < points.size(); i++) {
    //             float pt[] = (float[]) points.get(0);
    //             for (int j = 0; j < dim; j++)
    //                 fdata[i*dim + j] = pt[j];
    //         }
    //     } else {
    //         // In theory we could also handle automatically casting ints to floats this way
    //         throw new IllegalArgumentException("VxVertexAttrib() only accepts arraylists of double[] or float[]");
    //     }
    // }


    public int size()
    {
        return fdata.length;
    }



}