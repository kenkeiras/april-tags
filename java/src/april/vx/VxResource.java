package april.vx;

// managed resource
public class VxResource
{
    long id;

    int type;
    Object res;
    int count;
    int fieldwidth;

    // Arr must be a primitive array
    public VxResource(int type, Object arr, int count, int fieldwidth,  long id)
    {
        this.type = type;
        this.res = arr;
        this.count = count;
        this.fieldwidth = fieldwidth;
        this.id = id;
    }

    public VxResource(float buf[])
    {
        this(Vx.GL_FLOAT, buf, buf.length, 4, VxUtil.allocateID());
    }

    @Override
    public int hashCode()
    {
        return (int)((id >>> 32) ^ id);
    }

    @Override
    public boolean equals(Object other)
    {
        if (other instanceof VxResource)
            return ((VxResource)other).id == this.id;
        return false;
    }
}
