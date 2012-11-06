package april.vx;

import java.io.*;

// formerly vis.DoubleArray
public final class DoubleArray
{
    double data[] = new double[16];
    int  pos; // index of next index to write to.

    public DoubleArray()
    {
    }

    public void ensureSpace(int additionalCapacity)
    {
        if (pos + additionalCapacity < data.length)
            return;

        int newsize = 2 * data.length;

        while (newsize < pos + additionalCapacity)
            newsize *= 2;

        double f[] = new double[newsize];
        System.arraycopy(data, 0, f, 0, pos);
        data = f;
    }

    public void clear()
    {
        pos = 0;
    }

    public void add(double f)
    {
        ensureSpace(1);
        data[pos++] = f;
    }

    public double[] getData()
    {
        return data;
    }

    public int size()
    {
        return pos;
    }
}
