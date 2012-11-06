package april.vx;

import java.io.*;

public final class LongArray
{
    long data[] = new long[16];
    int  pos; // index of next index to write to.

    public LongArray()
    {
    }

    public void ensureSpace(int additionalCapacity)
    {
        if (pos + additionalCapacity < data.length)
            return;

        int newsize = 2 * data.length;

        while (newsize < pos + additionalCapacity)
            newsize *= 2;

        long f[] = new long[newsize];
        System.arraycopy(data, 0, f, 0, pos);
        data = f;
    }

    public long get(int idx)
    {
        return data[idx];
    }

    public void add(long f)
    {
        ensureSpace(1);
        data[pos++] = f;
    }

    public int bytesPerElement()
    {
        return 8;
    }

    public long[] getData()
    {
        return data;
    }

    public int size()
    {
        return pos;
    }
}
