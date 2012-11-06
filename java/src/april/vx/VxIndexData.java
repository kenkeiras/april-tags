package april.vx;

public class VxIndexData
{
    final long id = VxUtil.allocateID();

    final int data[];

    public VxIndexData(int data[])
    {
        this.data = data;
    }

}