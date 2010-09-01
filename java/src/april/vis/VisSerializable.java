package april.vis;

import java.io.*;

//XXX not usable yet
public interface VisSerializable
{
    // NOTE: You must ALSO specify a default constructor, e.g. MyVisObject(), if you extend this class

    // automatically writes appropriate header information for this class
    public void serialize(DataOutput out) throws IOException;
    public void unserialize(DataInput in) throws IOException;

}